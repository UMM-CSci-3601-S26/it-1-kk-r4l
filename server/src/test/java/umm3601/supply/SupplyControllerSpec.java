package umm3601.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;

@SuppressWarnings({ "MagicNumber", "UnusedImports" })
class SupplyControllerSpec {

  private SupplyController supplyController;

  private ObjectId plainPencilSupplyId;

  private static MongoClient mongoClient;
  private static MongoDatabase db;

  @Mock
  private Context ctx;

  @Captor
  private ArgumentCaptor<ArrayList<Supply>> supplyCaptor;

  @Captor
  private ArgumentCaptor<ArrayList<SupplyCoverage>> coverageCaptor;

  @BeforeAll
  static void setupAll() {
    String mongoAddr = System.getenv().getOrDefault("MONGO_ADDR", "localhost");

    mongoClient = MongoClients.create(
      MongoClientSettings.builder()
        .applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress(mongoAddr))))
        .build());
    db = mongoClient.getDatabase("test");
  }

  @AfterAll
  static void teardown() {
    db.drop();
    mongoClient.close();
  }

  @BeforeEach
  void setupEach() throws IOException {
    MockitoAnnotations.openMocks(this);

    // Setup supply requests
    MongoCollection<Document> supplyRequestDocuments = db.getCollection("supplyRequests");
    supplyRequestDocuments.drop();
    supplyRequestDocuments.insertMany(Arrays.asList(
      new Document()
        .append("school", "MAES")
        .append("grade", "2")
        .append("item", "pencil")
        .append("properties", Arrays.asList("#2"))
        .append("quantity", 12),
      new Document()
        .append("school", "MAES")
        .append("grade", "2")
        .append("item", "marker")
        .append("properties", Arrays.asList("black"))
        .append("quantity", 2)
    ));

    // Setup students
    MongoCollection<Document> studentDocuments = db.getCollection("students");
    studentDocuments.drop();
    studentDocuments.insertMany(Arrays.asList(
      new Document().append("first", "Alice").append("last", "Smith").append("school", "MAES").append("grade", "2"),
      new Document().append("first", "Bob").append("last", "Jones").append("school", "MAES").append("grade", "2"),
      new Document().append("first", "Charlie").append("last", "Brown").append("school", "MAES").append("grade", "2")
    ));

    // Setup supplies
    MongoCollection<Document> supplyDocuments = db.getCollection("supplies");
    supplyDocuments.drop();

    plainPencilSupplyId = new ObjectId();
    supplyDocuments.insertMany(Arrays.asList(
      new Document()
        .append("_id", plainPencilSupplyId)
        .append("item", "pencil")
        .append("properties", Arrays.asList("#2"))
        .append("description", "plain #2 pencil")
        .append("quantity", 30),
      new Document()
        .append("item", "pencil")
        .append("properties", Arrays.asList("#2", "Ticonderoga", "yellow"))
        .append("description", "yellow #2 Ticonderoga pencil")
        .append("quantity", 200),
      // Store properties as a String to verify robust parsing in controller.
      new Document()
        .append("item", "folder")
        .append("properties", "['red', 'plastic']")
        .append("description", "red plastic folder")
        .append("quantity", 12)
    ));

    supplyController = new SupplyController(db);
  }

  @Test
  void addsRoutes() {
    Javalin mockServer = mock(Javalin.class);
    supplyController.addRoutes(mockServer);
    verify(mockServer, Mockito.atLeast(3)).get(any(), any());
  }

  @Test
  void canGetSupplyWithExistentId() {
    when(ctx.pathParam("id")).thenReturn(plainPencilSupplyId.toHexString());

    supplyController.getSupply(ctx);

    verify(ctx).json(any(Supply.class));
    verify(ctx).status(HttpStatus.OK);
  }

  @Test
  void getSupplyWithBadIdThrowsBadRequestResponse() {
    when(ctx.pathParam("id")).thenReturn("bad");

    assertThrows(BadRequestResponse.class, () -> {
      supplyController.getSupply(ctx);
    });
  }

  @Test
  void getSupplyWithNonexistentIdThrowsNotFoundResponse() {
    when(ctx.pathParam("id")).thenReturn(new ObjectId().toHexString());

    assertThrows(NotFoundResponse.class, () -> {
      supplyController.getSupply(ctx);
    });
  }

  @Test
  void canGetSuppliesFilteredByProperties() {
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyController.PROPERTIES_KEY, Arrays.asList("red"));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParams(SupplyController.PROPERTIES_KEY)).thenReturn(Arrays.asList("red"));
    when(ctx.queryParam("sortby")).thenReturn("item");
    when(ctx.queryParam("sortorder")).thenReturn("asc");

    supplyController.getSupplies(ctx);

    verify(ctx).json(supplyCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<Supply> supplies = supplyCaptor.getValue();
    assertEquals(1, supplies.size());
    assertEquals("folder", supplies.get(0).item);
    assertTrue(Arrays.asList(supplies.get(0).properties).contains("red"));
  }

  @Test
  void canGetSuppliesWithDefaultSortingWhenSortParamsMissing() {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());
    when(ctx.queryParam("sortby")).thenReturn(null);
    when(ctx.queryParam("sortorder")).thenReturn(null);

    supplyController.getSupplies(ctx);

    verify(ctx).json(supplyCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<Supply> supplies = supplyCaptor.getValue();
    assertEquals(3, supplies.size());
    // Default sort is by item ascending, so folder should come before pencils.
    assertEquals("folder", supplies.get(0).item);
  }

  @Test
  void canGetSuppliesFilteredByItemCaseInsensitive() {
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyController.ITEM_KEY, Arrays.asList("PENCIL"));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyController.ITEM_KEY)).thenReturn("PENCIL");
    when(ctx.queryParam("sortby")).thenReturn("item");
    when(ctx.queryParam("sortorder")).thenReturn("asc");

    supplyController.getSupplies(ctx);

    verify(ctx).json(supplyCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<Supply> supplies = supplyCaptor.getValue();
    assertEquals(2, supplies.size());
    assertTrue(supplies.stream().allMatch(s -> "pencil".equalsIgnoreCase(s.item)));
  }

  @Test
  void getSupplyCoverageWithInvalidGradeThrowsBadRequestResponse() {
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyController.GRADE_KEY, Arrays.asList("banana"));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParamAsClass(SupplyController.GRADE_KEY, String.class))
      .thenThrow(new BadRequestResponse("To find supply coverage for a grade, use a valid grade option"));

    assertThrows(BadRequestResponse.class, () -> {
      supplyController.getSupplyCoverage(ctx);
    });
  }

  @Test
  void supplyCoverageMatchesSupplyWithStringEncodedProperties() {
    MongoCollection<Document> supplyRequestDocuments = db.getCollection("supplyRequests");

    supplyRequestDocuments.insertOne(new Document()
      .append("school", "MAES")
      .append("grade", "2")
      .append("item", "folder")
      .append("properties", Arrays.asList("red"))
      .append("quantity", 1));

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyController.ITEM_KEY, Arrays.asList("folder"));
    queryParams.put(SupplyController.PROPERTIES_KEY, Arrays.asList("red"));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyController.ITEM_KEY)).thenReturn("folder");
    when(ctx.queryParams(SupplyController.PROPERTIES_KEY)).thenReturn(Arrays.asList("red"));

    supplyController.getSupplyCoverage(ctx);

    verify(ctx).json(coverageCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<SupplyCoverage> coverage = coverageCaptor.getValue();
    assertEquals(1, coverage.size());

    SupplyCoverage folderCoverage = coverage.get(0);
    assertEquals("folder", folderCoverage.item);
    assertEquals(3, folderCoverage.neededQuantity);
    assertEquals(12, folderCoverage.onHandQuantity);
    assertEquals(3, folderCoverage.allocatedQuantity);
    assertEquals(0, folderCoverage.shortageQuantity);
    assertEquals(1, folderCoverage.extraPropertiesCount);
    assertEquals("red plastic folder", folderCoverage.bestSupplyDescription);
  }

  @Test
  void supplyCoverageMatchesItemAndPropertiesCaseInsensitive() {
    MongoCollection<Document> supplyDocuments = db.getCollection("supplies");
    ObjectId markerSupplyId = new ObjectId();
    supplyDocuments.insertOne(new Document()
      .append("_id", markerSupplyId)
      .append("item", "mArKeR")
      .append("properties", Arrays.asList("BLACK"))
      .append("description", "uppercase black marker stock")
      .append("quantity", 10));

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyController.ITEM_KEY, Arrays.asList("MARKER"));
    queryParams.put(SupplyController.PROPERTIES_KEY, Arrays.asList("black"));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyController.ITEM_KEY)).thenReturn("MARKER");
    when(ctx.queryParams(SupplyController.PROPERTIES_KEY)).thenReturn(Arrays.asList("black"));

    supplyController.getSupplyCoverage(ctx);

    verify(ctx).json(coverageCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<SupplyCoverage> coverage = coverageCaptor.getValue();
    assertEquals(1, coverage.size());

    SupplyCoverage markerCoverage = coverage.get(0);
    assertEquals("marker", markerCoverage.item);
    assertEquals(markerSupplyId.toHexString(), markerCoverage.bestSupplyId);
    assertEquals(6, markerCoverage.neededQuantity);
    assertEquals(6, markerCoverage.allocatedQuantity);
    assertEquals(0, markerCoverage.shortageQuantity);
  }

  @Test
  void supplyCoverageReturnsEmptyWhenRequestFiltersMatchNothing() {
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyController.SCHOOL_KEY, Arrays.asList("NO_SUCH_SCHOOL"));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyController.SCHOOL_KEY)).thenReturn("NO_SUCH_SCHOOL");

    supplyController.getSupplyCoverage(ctx);

    verify(ctx).json(coverageCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<SupplyCoverage> coverage = coverageCaptor.getValue();
    assertEquals(0, coverage.size());
  }

  @Test
  void supplyCoverageClampsNegativeSupplyQuantityToZeroAvailable() {
    MongoCollection<Document> supplyDocuments = db.getCollection("supplies");
    ObjectId negativeMarkerSupplyId = new ObjectId();
    supplyDocuments.insertOne(new Document()
      .append("_id", negativeMarkerSupplyId)
      .append("item", "marker")
      .append("properties", Arrays.asList("black"))
      .append("description", "negative marker stock")
      .append("quantity", -5));

    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());

    supplyController.getSupplyCoverage(ctx);

    verify(ctx).json(coverageCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<SupplyCoverage> coverage = coverageCaptor.getValue();
    SupplyCoverage markerCoverage = coverage.stream()
      .filter(entry -> "marker".equals(entry.item))
      .findFirst()
      .orElse(null);

    assertNotNull(markerCoverage);
    assertEquals(negativeMarkerSupplyId.toHexString(), markerCoverage.bestSupplyId);
    assertEquals(0, markerCoverage.onHandQuantity);
    assertEquals(0, markerCoverage.allocatedQuantity);
    assertEquals(6, markerCoverage.shortageQuantity);
  }

  @Test
  void supplyCoverageUsesLowestExtraPropertyMatch() {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());

    supplyController.getSupplyCoverage(ctx);

    verify(ctx).json(coverageCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<SupplyCoverage> coverage = coverageCaptor.getValue();
    assertEquals(2, coverage.size());

    SupplyCoverage pencilCoverage = coverage.stream()
      .filter(entry -> "pencil".equals(entry.item))
      .findFirst()
      .orElse(null);

    assertNotNull(pencilCoverage);
    assertEquals(36, pencilCoverage.neededQuantity);
    assertEquals(30, pencilCoverage.onHandQuantity);
    assertEquals(6, pencilCoverage.shortageQuantity);
    assertEquals(0, pencilCoverage.extraPropertiesCount);
    assertEquals(plainPencilSupplyId.toHexString(), pencilCoverage.bestSupplyId);

    SupplyCoverage markerCoverage = coverage.stream()
      .filter(entry -> "marker".equals(entry.item))
      .findFirst()
      .orElse(null);

    assertNotNull(markerCoverage);
    assertEquals(6, markerCoverage.neededQuantity);
    assertEquals(0, markerCoverage.onHandQuantity);
    assertEquals(6, markerCoverage.shortageQuantity);
    assertNull(markerCoverage.bestSupplyId);
  }

  @Test
  void supplyCoverageReflectsShortageWhenSameSupplyIsSharedAcrossNeeds() {
    MongoCollection<Document> supplyRequestDocuments = db.getCollection("supplyRequests");
    MongoCollection<Document> supplyDocuments = db.getCollection("supplies");
    MongoCollection<Document> studentDocuments = db.getCollection("students");

    // Add realistic student populations: more students, realistic per-student quantities.
    // These represent supply requests from teachers for their classes.
    // With 25 students in MAES grade 2:
    //   quantity 24 (pencils per student) => 25 * 24 = 600 needed
    // With 20 students in MAES grade 3:
    //   quantity 25 (pencils per student) => 20 * 25 = 500 needed
    // Total sharpened-related demand = 1100, but we only have 1000 in inventory
    // This creates a realistic shortage of 100.

    studentDocuments.drop();
    ArrayList<Document> gradeTwo = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      gradeTwo.add(
        new Document()
          .append("first", "Student" + i)
          .append("last", "GradeTwo")
          .append("school", "MAES")
          .append("grade", "2"));
    }
    studentDocuments.insertMany(gradeTwo);

    ArrayList<Document> gradeThree = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      gradeThree.add(
        new Document()
          .append("first", "Student" + i)
          .append("last", "GradeThree")
          .append("school", "MAES")
          .append("grade", "3"));
    }
    studentDocuments.insertMany(gradeThree);

    // Supply requests: realistic per-student quantities
    supplyRequestDocuments.insertMany(Arrays.asList(
      new Document()
        .append("school", "MAES")
        .append("grade", "2")
        .append("item", "pencil")
        .append("properties", Arrays.asList("sharpened"))
        .append("quantity", 24),
      new Document()
        .append("school", "MAES")
        .append("grade", "3")
        .append("item", "pencil")
        .append("properties", Arrays.asList("#2", "yellow", "sharpened"))
        .append("quantity", 25)
    ));

    ObjectId sharedSharpenedSupplyId = new ObjectId();
    supplyDocuments.insertOne(
      new Document()
        .append("_id", sharedSharpenedSupplyId)
        .append("item", "pencil")
        .append("properties", Arrays.asList("#2", "yellow", "sharpened"))
        .append("description", "shared sharpened pencil stock")
        .append("quantity", 1000));

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyController.ITEM_KEY, Arrays.asList("pencil"));
    queryParams.put(SupplyController.PROPERTIES_KEY, Arrays.asList("sharpened"));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyController.ITEM_KEY)).thenReturn("pencil");
    when(ctx.queryParams(SupplyController.PROPERTIES_KEY)).thenReturn(Arrays.asList("sharpened"));

    supplyController.getSupplyCoverage(ctx);

    verify(ctx, Mockito.atLeastOnce()).json(coverageCaptor.capture());
    verify(ctx, Mockito.atLeastOnce()).status(HttpStatus.OK);

    ArrayList<SupplyCoverage> coverage = coverageCaptor.getValue();
    assertEquals(2, coverage.size());

    // Verify realistic calculations:
    // Grade 2: 25 students * 24 quantity = 600 needed
    // Grade 3: 20 students * 25 quantity = 500 needed
    // Total: 1100 needed
    int totalNeeded = coverage.stream().mapToInt(entry -> entry.neededQuantity).sum();
    int totalAllocated = coverage.stream().mapToInt(entry -> entry.allocatedQuantity).sum();
    int totalShortage = coverage.stream().mapToInt(entry -> entry.shortageQuantity).sum();

    assertEquals(1100, totalNeeded);
    assertEquals(1000, totalAllocated);
    assertEquals(100, totalShortage);

    // Both needs map to the same supply, so matchedRequestCount=2 and total=1100
    for (SupplyCoverage entry : coverage) {
      assertEquals(sharedSharpenedSupplyId.toHexString(), entry.bestSupplyId);
      assertEquals(2, entry.matchedRequestCount);
      assertEquals(1100, entry.totalNeededAgainstMatchedSupply);
    }
  }
}
