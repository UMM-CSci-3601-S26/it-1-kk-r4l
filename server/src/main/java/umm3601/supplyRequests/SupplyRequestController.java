package umm3601.supplyRequests;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.mongojack.JacksonMongoCollection;

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
 * Controller that manages requests for info about supplyRequests.
 */
public class SupplyRequestController implements Controller {

  private static final String API_SUPPLY_REQUESTS = "/api/supplyRequests";
  private static final String API_SUPPLY_REQUEST_BY_ID = "/api/supplyRequests/{id}";
  private static final String API_SUPPLY_NEEDS = "/api/supplyNeeds";
  private static final String API_SUPPLY_NEEDS_GROUPED = "/api/supplyNeeds/grouped";
  static final String SCHOOL_KEY = "school";
  static final String GRADE_KEY = "grade";
  static final String ITEM_KEY = "item";
  static final String PROPERTIES_KEY = "properties";
  static final String SORT_ORDER_KEY = "sortorder";

  private static final String GRADE_REGEX = "^(Pre K|kindergarten|1|2|3|4|5|6|7|HS)$";

  private final JacksonMongoCollection<SupplyRequest> supplyRequestCollection;

  /**
   * Construct a controller for supplyRequests.
   *
   * @param database the database containing user data
   */
  public SupplyRequestController(MongoDatabase database) {
    supplyRequestCollection = JacksonMongoCollection.builder().build(
        database,
        "supplyRequests",
        SupplyRequest.class,
        UuidRepresentation.STANDARD);
  }

  /**
   * Set the JSON body of the response to be the single supplyRequest
   * specified by the `id` parameter in the request
   *
   * @param ctx a Javalin HTTP context
   */
  public void getSupplyRequest(Context ctx) {
    String id = ctx.pathParam("id");
    SupplyRequest supplyRequest;

    try {
      supplyRequest = supplyRequestCollection.find(eq("_id", new ObjectId(id))).first();
    } catch (IllegalArgumentException e) {
      throw new BadRequestResponse("The requested supplyRequest id wasn't a legal Mongo Object ID.");
    }
    if (supplyRequest == null) {
      throw new NotFoundResponse("The requested supplyRequest was not found");
    } else {
      ctx.json(supplyRequest);
      ctx.status(HttpStatus.OK);
    }
  }

  /**
   * Set the JSON body of the response to be a list of all the supplyRequests returned from the database
   * that match any requested filters and ordering
   *
   * @param ctx a Javalin HTTP context
   */
  public void getSupplyRequests(Context ctx) {
    Bson combinedFilter = constructFilter(ctx);
    Bson sortingOrder = constructSortingOrder(ctx);

    // All three of the find, sort, and into steps happen "in parallel" inside the
    // database system. So MongoDB is going to find the supplyRequests with the specified
    // properties, return those sorted in the specified manner, and put the
    // results into an initially empty ArrayList.
    ArrayList<SupplyRequest> matchingSupplyRequests = supplyRequestCollection
      .find(combinedFilter)
      .sort(sortingOrder)
      .into(new ArrayList<>());

    // Set the JSON body of the response to be the list of supplyRequests returned by the database.
    // According to the Javalin documentation (https://javalin.io/documentation#context),
    // this calls result(jsonString), and also sets content type to json
    ctx.json(matchingSupplyRequests);

    // Explicitly set the context status to OK
    ctx.status(HttpStatus.OK);
  }

  /**
   * Construct a Bson filter document to use in the `find` method based on the
   * query parameters from the context.
   *
   * This checks for the presence of the `age`, `company`, and `role` query
   * parameters and constructs a filter document that will match supplyRequests with
   * the specified values for those fields.
   *
   * @param ctx a Javalin HTTP context, which contains the query parameters
   *    used to construct the filter
   * @return a Bson filter document that can be used in the `find` method
   *   to filter the database collection of supplyRequests
   */
  private Bson constructFilter(Context ctx) {
    List<Bson> filters = new ArrayList<>(); // start with an empty list of filters

    if (ctx.queryParamMap().containsKey(SCHOOL_KEY)) {
      Pattern pattern = Pattern.compile(Pattern.quote(ctx.queryParam(SCHOOL_KEY)), Pattern.CASE_INSENSITIVE);
      filters.add(regex(SCHOOL_KEY, pattern));
    }
    if (ctx.queryParamMap().containsKey(GRADE_KEY)) {
      String grade = ctx.queryParamAsClass(GRADE_KEY, String.class)
        .check(it -> it.matches(GRADE_REGEX), "To find a supply request associated with a grade, use a valid grade option")
        .get();
      filters.add(eq(GRADE_KEY, grade));
    }
    if (ctx.queryParamMap().containsKey(ITEM_KEY)) {
      // we want to get exactly 'pencil' and not get 'colored pencils' or 'pencil box', so use ^ and $
      Pattern pattern = Pattern.compile('^' + ctx.queryParam(ITEM_KEY) + '$', Pattern.CASE_INSENSITIVE);
      filters.add(regex(ITEM_KEY, pattern));
    }
    if (ctx.queryParamMap().containsKey(PROPERTIES_KEY)) {
      List<String> targetProperties = ctx.queryParams(PROPERTIES_KEY);
      //db.collection.find({ properties: { $all: ["#2", "yellow"] } })
      filters.add(Filters.all(PROPERTIES_KEY, targetProperties));
    }

    // Combine the list of filters into a single filtering document.
    Bson combinedFilter = filters.isEmpty() ? new Document() : and(filters);

    return combinedFilter;
  }

  /**
   * Construct a Bson sorting document to use in the `sort` method based on the
   * query parameters from the context.
   *
   * This checks for the presence of the `sortby` and `sortorder` query
   * parameters and constructs a sorting document that will sort supplyRequests by
   * the specified field in the specified order. If the `sortby` query
   * parameter is not present, it defaults to "grade". If the `sortorder`
   * query parameter is not present, it defaults to "asc".
   *
   * @param ctx a Javalin HTTP context, which contains the query parameters
   *   used to construct the sorting order
   * @return a Bson sorting document that can be used in the `sort` method
   *  to sort the database collection of supplyRequests
   */
  private Bson constructSortingOrder(Context ctx) {
    // Sort the results. Use the `sortby` query param (default "name")
    // as the field to sort by, and the query param `sortorder` (default
    // "asc") to specify the sort order.
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortby"), "grade");
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortorder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);
    return sortingOrder;
  }

  /**
   * Set the JSON body of the response to be a list of all the items, properties, quantities,
   * and (number of) students returned from the database, grouped by item
   *
   * @param ctx a Javalin HTTP context that provides the query parameters
   *   used to sort the results. We support either sorting by company name
   *   (in either `asc` or `desc` order) or by the number of users in the
   *   company (`count`, also in either `asc` or `desc` order).
   */
  public void calculateNeed(Context ctx) {

    List<Bson> pipeline = Arrays.asList(
      // Filter supply requests using any supported query params.
      Aggregates.match(itemFilter(ctx)),
      // Filter out supply requests with missing or null item field
      new Document("$match", new Document("item", new Document("$exists", true).append("$ne", null))),
      // Join each supply request to matching students (same school + grade)
      // and count those matching students.
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
      // Extract the number of students in matching school/grade.
      new Document("$addFields", new Document("studentCount",
        new Document("$ifNull", Arrays.asList(new Document("$first", "$studentCounts.count"), 0)))),
      // Compute total amount needed: number of matching students * per-student quantity.
      new Document("$addFields", new Document("count",
        new Document("$multiply", Arrays.asList(
          new Document("$ifNull", Arrays.asList("$quantity", 0)),
          "$studentCount"
        )))),
      // Output only fields needed by SupplyNeedContribution.
      new Document("$match", new Document("studentCount", new Document("$gt", 0))),
      new Document("$project", new Document("_id", new Document("$toString", "$_id"))
        .append("school", 1)
        .append("grade", 1)
        .append("item", 1)
        .append("properties", 1)
        .append("quantity", 1)
        .append("studentCount", 1)
        .append("count", 1)),
      new Document("$sort", new Document("item", 1).append("quantity", 1))
    );

    ArrayList<SupplyNeedContribution> matchingNeeds = supplyRequestCollection
      .aggregate(pipeline, SupplyNeedContribution.class)
      .into(new ArrayList<>());

    ctx.json(matchingNeeds);
    ctx.status(HttpStatus.OK);
  }

  /**
   * Set the JSON body of the response to grouped supply needs where each entry
   * corresponds to a unique (item, properties) pair.
   *
   * For each group we include:
   * - the sum of `count` across all contributing supply requests (`totalCount`)
   * - the list of supply requests that generated each `studentCount` and `count`
   *
   * @param ctx a Javalin HTTP context that provides optional filtering query parameters
   */
  public void calculateNeedGrouped(Context ctx) {

    List<Bson> pipeline = Arrays.asList(
      Aggregates.match(itemFilter(ctx)),
      // Filter out supply requests with missing or null item field
      new Document("$match", new Document("item", new Document("$exists", true).append("$ne", null))),
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
      new Document("$addFields", new Document("studentCount",
        new Document("$ifNull", Arrays.asList(new Document("$first", "$studentCounts.count"), 0)))),
      new Document("$addFields", new Document("count",
        new Document("$multiply", Arrays.asList(
          new Document("$ifNull", Arrays.asList("$quantity", 0)),
          "$studentCount"
        )))),
      new Document("$match", new Document("studentCount", new Document("$gt", 0))),
      new Document("$group", new Document("_id", new Document("item", "$item")
        .append("properties", "$properties"))
        .append("totalCount", new Document("$sum", "$count"))
        .append("supplyRequests", new Document("$push", new Document("_id", new Document("$toString", "$_id"))
          .append("school", "$school")
          .append("grade", "$grade")
          .append("quantity", "$quantity")
          .append("studentCount", "$studentCount")
          .append("count", "$count")))),
      new Document("$project", new Document("_id", 0)
        .append("item", "$_id.item")
        .append("properties", new Document("$cond", Arrays.asList(
          new Document("$eq", Arrays.asList("$_id.properties", null)),
          Arrays.asList(),
          "$_id.properties")))
        .append("totalCount", 1)
        .append("supplyRequests", 1)),
      new Document("$sort", new Document("item", 1).append("totalCount", -1))
    );

    ArrayList<SupplyNeedGroup> groupedNeeds = supplyRequestCollection
      .aggregate(pipeline, SupplyNeedGroup.class)
      .into(new ArrayList<>());

    ctx.json(groupedNeeds);
    ctx.status(HttpStatus.OK);
  }
  // public String _id;
  // public String item;
  // public String[] properties;
  // public int quantity;
  // public int count;


  /**
   * Construct a Bson filter document to use in the `find` method based on the
   * query parameters from the context.
   *
   * @param ctx a Javalin HTTP context, which contains the query parameters
   *    used to construct the filter
   * @return a Bson filter document that can be used in the `find` method
   *   to filter the database collection of supplyRequests
   */
  private Bson itemFilter(Context ctx) {
    List<Bson> filters = new ArrayList<>(); // start with an empty list of filters

    if (ctx.queryParamMap().containsKey(SCHOOL_KEY)) {
      Pattern pattern = Pattern.compile(Pattern.quote(ctx.queryParam(SCHOOL_KEY)), Pattern.CASE_INSENSITIVE);
      filters.add(regex(SCHOOL_KEY, pattern));
    }
    if (ctx.queryParamMap().containsKey(GRADE_KEY)) {
      String grade = ctx.queryParamAsClass(GRADE_KEY, String.class)
        .check(it -> it.matches(GRADE_REGEX), "To find a supply request associated with a grade, use a valid grade option")
        .get();
      filters.add(eq(GRADE_KEY, grade));
    }
    if (ctx.queryParamMap().containsKey(ITEM_KEY)) {
      // we want to get exactly 'pencil' and not get 'colored pencils' or 'pencil box', so use ^ and $
      Pattern pattern = Pattern.compile('^' + ctx.queryParam(ITEM_KEY) + '$', Pattern.CASE_INSENSITIVE);
      filters.add(regex(ITEM_KEY, pattern));
    }
    if (ctx.queryParamMap().containsKey(PROPERTIES_KEY)) {
      List<String> targetProperties = ctx.queryParams(PROPERTIES_KEY);
      //db.collection.find({ properties: { $all: ["#2", "yellow"] } })
      filters.add(Filters.all(PROPERTIES_KEY, targetProperties));
    }

    // Combine the list of filters into a single filtering document.
    Bson combinedFilter = filters.isEmpty() ? new Document() : and(filters);

    return combinedFilter;
  }

  /**
   * Sets up routes for the `supplyRequest` collection endpoints.
   *
   * @param server The Javalin server instance
   */
  @Override
  public void addRoutes(Javalin server) {
    // Get the specified user
    server.get(API_SUPPLY_REQUEST_BY_ID, this::getSupplyRequest);

    // List supplyRequests, filtered using query parameters
    server.get(API_SUPPLY_REQUESTS, this::getSupplyRequests);

    // Calculate need based on number of students (grade and school) and supply requests
    server.get(API_SUPPLY_NEEDS, this::calculateNeed);

    // Calculate grouped supply needs by (item, properties), including total counts
    // and per-supplyRequest contributions.
    server.get(API_SUPPLY_NEEDS_GROUPED, this::calculateNeedGrouped);

    // Get the supplyRequests, possibly filtered, grouped by company
    // server.get("/api/supplyRequestsByCompany", this::getUsersGroupedByCompany);

    // Add new user with the user info being in the JSON body
    // of the HTTP request
    // server.post(API_SUPPLY_REQUESTS, this::addNewSupplyRequest);

    // Delete the specified user
    // server.delete(API_SUPPLY_REQUEST_BY_ID, this::deleteSupplyRequest);
  }
}
