package umm3601.supply;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.mongojack.JacksonMongoCollection;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import umm3601.Controller;

/**
 * Controller that manages requests for supplies and supply coverage.
 */
public class SupplyController implements Controller {

  private static final String API_SUPPLIES = "/api/supplies";
  private static final String API_SUPPLY_BY_ID = "/api/supplies/{id}";
  private static final String API_SUPPLY_COVERAGE = "/api/supplies/coverage";

  static final String SCHOOL_KEY = "school";
  static final String GRADE_KEY = "grade";
  static final String ITEM_KEY = "item";
  static final String PROPERTIES_KEY = "properties";

  private static final String GRADE_REGEX = "^(Pre K|kindergarten|1|2|3|4|5|6|7|HS)$";

  private final JacksonMongoCollection<Supply> supplyCollection;
  private final MongoCollection<Document> supplyDocuments;
  private final MongoCollection<Document> supplyRequestDocuments;

  /**
   * Construct a controller for supplies.
   *
   * @param database the database containing supply and supplyRequest data
   */
  public SupplyController(MongoDatabase database) {
    supplyCollection = JacksonMongoCollection.builder().build(
      database,
      "supplies",
      Supply.class,
      UuidRepresentation.STANDARD);

    supplyDocuments = database.getCollection("supplies");
    supplyRequestDocuments = database.getCollection("supplyRequests");
  }

  /**
   * Get one supply by id.
   *
   * @param ctx a Javalin HTTP context
   */
  public void getSupply(Context ctx) {
    String id = ctx.pathParam("id");
    Supply supply;

    try {
      supply = supplyCollection.find(eq("_id", new ObjectId(id))).first();
    } catch (IllegalArgumentException e) {
      throw new BadRequestResponse("The requested supply id wasn't a legal Mongo Object ID.");
    }

    if (supply == null) {
      throw new NotFoundResponse("The requested supply was not found");
    }

    ctx.json(supply);
    ctx.status(HttpStatus.OK);
  }

  /**
   * Get supplies with optional filtering and sorting.
   *
   * @param ctx a Javalin HTTP context
   */
  public void getSupplies(Context ctx) {
    Bson databaseFilter = constructSupplyFilter(ctx);
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortby"), "item");
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortorder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ? Sorts.descending(sortBy) : Sorts.ascending(sortBy);

    ArrayList<Supply> supplies = new ArrayList<>();
    for (Document doc : supplyDocuments.find(databaseFilter).sort(sortingOrder)) {
      Supply converted = documentToSupply(doc);
      if (converted != null && matchesRequestedProperties(ctx, converted)) {
        supplies.add(converted);
      }
    }

    ctx.json(supplies);
    ctx.status(HttpStatus.OK);
  }

  /**
   * Calculate coverage between requested supplies (student-driven need) and on-hand supplies.
   *
   * @param ctx a Javalin HTTP context
   */
  public void getSupplyCoverage(Context ctx) {
    // Calculate grouped supply needs using aggregation pipeline
    ArrayList<Document> neededGroups = calculateGroupedNeeds(ctx);

    // Allocate limited on-hand quantities across all need groups.
    // We process more-specific needs first (more properties) because they generally
    // have fewer matching options.
    neededGroups.sort(Comparator
      .comparingInt((Document needed) -> asStringList(needed.get("properties")).size()).reversed()
      .thenComparingInt(needed -> needed.getInteger("totalCount", 0)).reversed());

    Map<String, Integer> remainingBySupplyId = new HashMap<>();
    ArrayList<SupplyCoverage> results = new ArrayList<>();

    for (Document needed : neededGroups) {
      String item = needed.getString("item");
      List<String> requestedProperties = asStringList(needed.get("properties"));
      int neededQuantity = needed.getInteger("totalCount", 0);

      SupplyMatch bestMatch = findBestMatchingSupply(item, requestedProperties, remainingBySupplyId);

      SupplyCoverage coverage = new SupplyCoverage();
      coverage.item = item;
      coverage.requestedProperties = requestedProperties.toArray(new String[0]);
      coverage.neededQuantity = neededQuantity;

      if (bestMatch != null) {
        List<String> bestProperties = asStringList(bestMatch.supply.properties);
        int extraProperties = Math.max(0, bestProperties.size() - requestedProperties.size());
        int availableNow = Math.max(0, bestMatch.availableQuantity);
        int allocated = Math.min(neededQuantity, availableNow);
        int remainingAfter = Math.max(0, availableNow - allocated);

        coverage.bestSupplyId = bestMatch.supply._id;
        coverage.bestSupplyDescription = bestMatch.supply.description;
        coverage.bestSupplyProperties = bestMatch.supply.properties;
        // "onHandQuantity" here represents quantity available at match time,
        // after prior allocations have been accounted for.
        coverage.onHandQuantity = availableNow;
        coverage.allocatedQuantity = allocated;
        coverage.remainingQuantityAfterAllocation = remainingAfter;
        coverage.extraPropertiesCount = extraProperties;
        coverage.shortageQuantity = Math.max(0, neededQuantity - allocated);

        remainingBySupplyId.put(bestMatch.supply._id, remainingAfter);
      } else {
        coverage.bestSupplyId = null;
        coverage.bestSupplyDescription = null;
        coverage.bestSupplyProperties = new String[0];
        coverage.onHandQuantity = 0;
        coverage.allocatedQuantity = 0;
        coverage.remainingQuantityAfterAllocation = 0;
        coverage.extraPropertiesCount = 0;
        coverage.shortageQuantity = neededQuantity;
      }

      coverage.matchedRequestCount = 0;
      coverage.totalNeededAgainstMatchedSupply = 0;

      results.add(coverage);
    }

    // Summarize how many need groups share the same matched supply and their total demand.
    Map<String, Integer> matchedCounts = new HashMap<>();
    Map<String, Integer> matchedDemandTotals = new HashMap<>();
    for (SupplyCoverage coverage : results) {
      if (coverage.bestSupplyId != null) {
        matchedCounts.put(coverage.bestSupplyId, matchedCounts.getOrDefault(coverage.bestSupplyId, 0) + 1);
        matchedDemandTotals.put(
          coverage.bestSupplyId,
          matchedDemandTotals.getOrDefault(coverage.bestSupplyId, 0) + coverage.neededQuantity);
      }
    }

    for (SupplyCoverage coverage : results) {
      if (coverage.bestSupplyId != null) {
        coverage.matchedRequestCount = matchedCounts.getOrDefault(coverage.bestSupplyId, 1);
        coverage.totalNeededAgainstMatchedSupply = matchedDemandTotals.getOrDefault(
          coverage.bestSupplyId,
          coverage.neededQuantity);
      }
    }

    results.sort(Comparator
      .comparing((SupplyCoverage coverage) -> coverage.item, String.CASE_INSENSITIVE_ORDER)
      .thenComparing(coverage -> Arrays.toString(coverage.requestedProperties), String.CASE_INSENSITIVE_ORDER));

    ctx.json(results);
    ctx.status(HttpStatus.OK);
  }

  private SupplyMatch findBestMatchingSupply(
    String item,
    List<String> requestedProperties,
    Map<String, Integer> remainingBySupplyId) {

    Pattern pattern = Pattern.compile('^' + item + '$', Pattern.CASE_INSENSITIVE);
    List<SupplyMatch> candidates = new ArrayList<>();

    for (Document doc : supplyDocuments.find(regex(ITEM_KEY, pattern))) {
      Supply supply = documentToSupply(doc);
      if (supply == null || supply.properties == null) {
        continue;
      }

      List<String> supplyProperties = asStringList(supply.properties);
      if (containsAllIgnoreCase(supplyProperties, requestedProperties)) {
        int available = remainingBySupplyId.getOrDefault(supply._id, supply.quantity);
        candidates.add(new SupplyMatch(supply, Math.max(0, available)));
      }
    }

    if (candidates.isEmpty()) {
      return null;
    }

    candidates.sort(Comparator
      .comparingInt((SupplyMatch candidate) ->
        asStringList(candidate.supply.properties).size() - requestedProperties.size())
      .thenComparing((SupplyMatch candidate) -> candidate.availableQuantity, Comparator.reverseOrder())
      .thenComparing(
        candidate -> Objects.requireNonNullElse(candidate.supply.description, ""),
        String.CASE_INSENSITIVE_ORDER));

    return candidates.get(0);
  }

  private static class SupplyMatch {
    private final Supply supply;
    private final int availableQuantity;

    SupplyMatch(Supply supply, int availableQuantity) {
      this.supply = supply;
      this.availableQuantity = availableQuantity;
    }
  }

  private Bson constructSupplyFilter(Context ctx) {
    List<Bson> filters = new ArrayList<>();

    if (ctx.queryParamMap().containsKey(ITEM_KEY)) {
      Pattern pattern = Pattern.compile('^' + ctx.queryParam(ITEM_KEY) + '$', Pattern.CASE_INSENSITIVE);
      filters.add(regex(ITEM_KEY, pattern));
    }

    return filters.isEmpty() ? new Document() : and(filters);
  }

  private Bson constructRequestFilter(Context ctx) {
    List<Bson> filters = new ArrayList<>();

    if (ctx.queryParamMap().containsKey(SCHOOL_KEY)) {
      Pattern pattern = Pattern.compile(Pattern.quote(ctx.queryParam(SCHOOL_KEY)), Pattern.CASE_INSENSITIVE);
      filters.add(regex(SCHOOL_KEY, pattern));
    }
    if (ctx.queryParamMap().containsKey(GRADE_KEY)) {
      String grade = ctx.queryParamAsClass(GRADE_KEY, String.class)
        .check(it -> it.matches(GRADE_REGEX),
          "To find supply coverage for a grade, use a valid grade option")
        .get();
      filters.add(eq(GRADE_KEY, grade));
    }
    if (ctx.queryParamMap().containsKey(ITEM_KEY)) {
      Pattern pattern = Pattern.compile('^' + ctx.queryParam(ITEM_KEY) + '$', Pattern.CASE_INSENSITIVE);
      filters.add(regex(ITEM_KEY, pattern));
    }
    if (ctx.queryParamMap().containsKey(PROPERTIES_KEY)) {
      List<String> targetProperties = ctx.queryParams(PROPERTIES_KEY);
      filters.add(Filters.all(PROPERTIES_KEY, targetProperties));
    }

    return filters.isEmpty() ? new Document() : and(filters);
  }

  private boolean matchesRequestedProperties(Context ctx, Supply supply) {
    if (!ctx.queryParamMap().containsKey(PROPERTIES_KEY)) {
      return true;
    }
    List<String> targetProperties = ctx.queryParams(PROPERTIES_KEY);
    return containsAllIgnoreCase(asStringList(supply.properties), targetProperties);
  }

  private boolean containsAllIgnoreCase(List<String> source, List<String> required) {
    List<String> normalizedSource = source.stream()
      .map(value -> value.toLowerCase().trim())
      .collect(Collectors.toList());

    for (String needed : required) {
      if (!normalizedSource.contains(needed.toLowerCase().trim())) {
        return false;
      }
    }
    return true;
  }

  private Supply documentToSupply(Document doc) {
    if (doc == null) {
      return null;
    }

    Supply supply = new Supply();
    Object id = doc.get("_id");
    if (id instanceof ObjectId) {
      supply._id = ((ObjectId) id).toHexString();
    } else {
      supply._id = id == null ? null : id.toString();
    }

    supply.item = doc.getString("item");
    supply.description = doc.getString("description");
    supply.quantity = doc.getInteger("quantity", 0);

    List<String> propertyList = asStringList(doc.get("properties"));
    supply.properties = propertyList.toArray(new String[0]);

    return supply;
  }

  @SuppressWarnings("unchecked")
  private List<String> asStringList(Object value) {
    if (value == null) {
      return new ArrayList<>();
    }

    if (value instanceof String[]) {
      return Arrays.stream((String[]) value)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(ArrayList::new));
    }

    if (value instanceof List<?>) {
      ArrayList<String> list = new ArrayList<>();
      for (Object element : (List<Object>) value) {
        if (element != null) {
          list.add(element.toString());
        }
      }
      return list;
    }

    if (value instanceof String) {
      String stringValue = ((String) value).trim();
      if (stringValue.isEmpty()) {
        return new ArrayList<>();
      }
      String cleaned = stringValue
        .replace("[", "")
        .replace("]", "")
        .replace("'", "")
        .trim();
      if (cleaned.isEmpty()) {
        return new ArrayList<>();
      }
      return Arrays.stream(cleaned.split(","))
        .map(String::trim)
        .filter(token -> !token.isEmpty())
        .collect(Collectors.toCollection(ArrayList::new));
    }

    return new ArrayList<>(Collections.singletonList(value.toString()));
  }

  /**
   * Calculate grouped supply needs from supply requests and student counts.
   *
   * This runs an aggregation pipeline that:
   * 1. Filters supply requests based on the provided filter
   * 2. Removes requests with missing/null items
   * 3. Looks up student counts for each request's school/grade
   * 4. Calculates total items needed (studentCount × quantity)
   * 5. Filters out requests with zero students
   * 6. Groups by item and properties
   * 7. Returns total count per group
   *
   * @param ctx a Javalin HTTP context that provides query parameters for filtering
   * @return List of documents with item, properties, and totalCount fields
   */
  private ArrayList<Document> calculateGroupedNeeds(Context ctx) {
    Bson requestFilter = constructRequestFilter(ctx);

    List<Bson> pipeline = Arrays.asList(
      // Stage 1: Filter supply requests using the provided filter
      Aggregates.match(requestFilter),
      // Stage 2: Filter out supply requests with missing or null item field
      new Document("$match", new Document("item", new Document("$exists", true).append("$ne", null))),
      // Stage 3: Perform a $lookup join with the students collection
      new Document("$lookup", new Document("from", "students")
        .append("let", new Document("requestSchool", "$school").append("requestGrade", "$grade"))
        .append("pipeline", Arrays.asList(
          new Document("$match", new Document("$expr", new Document("$and", Arrays.asList(
            new Document("$eq", Arrays.asList("$school", "$$requestSchool")),
            new Document("$eq", Arrays.asList("$grade", "$$requestGrade"))
          )))),
          new Document("$count", "count")
        ))
        .append("as", "studentCounts")),
      // Stage 4: Extract the student count from the studentCounts array
      new Document("$addFields", new Document("studentCount",
        new Document("$ifNull", Arrays.asList(new Document("$first", "$studentCounts.count"), 0)))),
      // Stage 5: Calculate the total number of items needed
      new Document("$addFields", new Document("count",
        new Document("$multiply", Arrays.asList(
          new Document("$ifNull", Arrays.asList("$quantity", 0)),
          "$studentCount"
        )))),
      // Stage 6: Filter out supply requests where no students match
      new Document("$match", new Document("studentCount", new Document("$gt", 0))),
      // Stage 7: Group by item and properties, summing the counts
      new Document("$group", new Document("_id", new Document("item", "$item").append("properties", "$properties"))
        .append("totalCount", new Document("$sum", "$count"))),
      // Stage 8: Project to clean format
      new Document("$project", new Document("_id", 0)
        .append("item", "$_id.item")
        .append("properties", "$_id.properties")
        .append("totalCount", 1))
    );

    return supplyRequestDocuments.aggregate(pipeline).into(new ArrayList<>());
  }

  /**
   * Sets up routes for the `supply` collection endpoints.
   *
   * @param server The Javalin server instance
   */
  @Override
  public void addRoutes(Javalin server) {
    server.get(API_SUPPLY_COVERAGE, this::getSupplyCoverage);
    server.get(API_SUPPLIES, this::getSupplies);
    server.get(API_SUPPLY_BY_ID, this::getSupply);
  }
}
