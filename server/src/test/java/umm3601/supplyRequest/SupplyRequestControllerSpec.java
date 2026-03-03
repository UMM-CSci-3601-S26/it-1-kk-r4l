package umm3601.supplyRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import io.javalin.validation.Validation;
import io.javalin.validation.Validator;

/**
 * Tests the logic of the SupplyRequestController
 *
 * @throws IOException
 */
@SuppressWarnings({ "MagicNumber", "UnusedImports" })
class SupplyRequestControllerSpec {

  private SupplyRequestController supplyRequestController;

  private ObjectId pencilId2;
  private ObjectId pencilIdTico;

  private static MongoClient mongoClient;
  private static MongoDatabase db;

  @Mock
  private Context ctx;

  @Captor
  private ArgumentCaptor<ArrayList<SupplyNeedContribution>> supplyNeedContributionCaptor;

  @Captor
  private ArgumentCaptor<ArrayList<SupplyNeedGroup>> supplyNeedGroupCaptor;

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

    // Setup database with test supply requests
    MongoCollection<Document> supplyRequestDocuments = db.getCollection("supplyRequests");
    supplyRequestDocuments.drop();
    List<Document> testSupplyRequests = new ArrayList<>();

    // Pencil with #2 and Ticonderoga properties for MAES grade 2
    pencilIdTico = new ObjectId();
    Document pencilTico = new Document()
        .append("_id", pencilIdTico)
        .append("school", "MAES")
        .append("grade", "2")
        .append("description", "Ticonderoga Pencils")
        .append("item", "pencil")
        .append("properties", Arrays.asList("#2", "ticonderoga"))
        .append("quantity", 12);
    testSupplyRequests.add(pencilTico);

    // Pencil with #2 property for MAHS HS
    testSupplyRequests.add(
        new Document()
            .append("school", "MAHS")
            .append("grade", "HS")
            .append("description", "High School Pencils")
            .append("item", "pencil")
            .append("properties", Arrays.asList("#2"))
            .append("quantity", 24));

    // Pencil with #2 property for MAES grade 4
    pencilId2 = new ObjectId();
    Document pencil2 = new Document()
        .append("_id", pencilId2)
        .append("school", "MAES")
        .append("grade", "4")
        .append("description", "Grade 4 Pencils")
        .append("item", "pencil")
        .append("properties", Arrays.asList("#2"))
        .append("quantity", 12);
    testSupplyRequests.add(pencil2);

    // Folder request for MAES grade 2
    testSupplyRequests.add(
        new Document()
            .append("school", "MAES")
            .append("grade", "2")
            .append("description", "Red folders")
            .append("item", "folder")
            .append("properties", Arrays.asList("pocket", "red"))
            .append("quantity", 1));

    // Supply request with missing item (should be filtered out)
    testSupplyRequests.add(
        new Document()
            .append("school", "MAES")
            .append("grade", "3")
            .append("description", "Crayons without item field")
            .append("quantity", 1));

    // Supply request with null item (should be filtered out)
    testSupplyRequests.add(
        new Document()
            .append("school", "MAHS")
            .append("grade", "HS")
            .append("description", "Something without a proper item")
            .append("item", null)
            .append("quantity", 1));

    supplyRequestDocuments.insertMany(testSupplyRequests);

    // Setup students collection
    MongoCollection<Document> studentDocuments = db.getCollection("students");
    studentDocuments.drop();
    List<Document> testStudents = new ArrayList<>();

    // 3 students in MAES grade 2
    testStudents.add(
        new Document()
            .append("first", "Alice")
            .append("last", "Smith")
            .append("school", "MAES")
            .append("grade", "2"));
    testStudents.add(
        new Document()
            .append("first", "Bob")
            .append("last", "Jones")
            .append("school", "MAES")
            .append("grade", "2"));
    testStudents.add(
        new Document()
            .append("first", "Charlie")
            .append("last", "Brown")
            .append("school", "MAES")
            .append("grade", "2"));

    // 2 students in MAES grade 4
    testStudents.add(
        new Document()
            .append("first", "Diana")
            .append("last", "Prince")
            .append("school", "MAES")
            .append("grade", "4"));
    testStudents.add(
        new Document()
            .append("first", "Eve")
            .append("last", "Davis")
            .append("school", "MAES")
            .append("grade", "4"));

    // 5 students in MAHS HS
    testStudents.add(
        new Document()
            .append("first", "Frank")
            .append("last", "Miller")
            .append("school", "MAHS")
            .append("grade", "HS"));
    testStudents.add(
        new Document()
            .append("first", "Grace")
            .append("last", "Lee")
            .append("school", "MAHS")
            .append("grade", "HS"));
    testStudents.add(
        new Document()
            .append("first", "Henry")
            .append("last", "Wilson")
            .append("school", "MAHS")
            .append("grade", "HS"));
    testStudents.add(
        new Document()
            .append("first", "Ivy")
            .append("last", "Taylor")
            .append("school", "MAHS")
            .append("grade", "HS"));
    testStudents.add(
        new Document()
            .append("first", "Jack")
            .append("last", "Anderson")
            .append("school", "MAHS")
            .append("grade", "HS"));

    studentDocuments.insertMany(testStudents);

    supplyRequestController = new SupplyRequestController(db);
  }

  @Test
  void addsRoutes() {
    Javalin mockServer = mock(Javalin.class);
    supplyRequestController.addRoutes(mockServer);
    verify(mockServer, Mockito.atLeast(3)).get(any(), any());
  }

  @Test
  void canGetAllSupplyNeeds() throws IOException {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());

    supplyRequestController.calculateNeed(ctx);

    verify(ctx).json(supplyNeedContributionCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<SupplyNeedContribution> needs = supplyNeedContributionCaptor.getValue();
    // Should have 4 items: 2 pencil requests + 1 folder request
    // (2 requests are filtered out: missing item and null item)
    assertEquals(4, needs.size());
  }

  /**
   * Test filtering by item. When filtering for "pencil", we should get
   * all pencil requests with their calculated counts based on student populations.
   */
  @Test
  void canGetSupplyNeedsFilteredByItem() throws IOException {
    String targetItem = "pencil";

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyRequestController.ITEM_KEY, Arrays.asList(targetItem));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyRequestController.ITEM_KEY)).thenReturn(targetItem);

    supplyRequestController.calculateNeed(ctx);

    verify(ctx).json(supplyNeedContributionCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<SupplyNeedContribution> needs = supplyNeedContributionCaptor.getValue();
    // Should have 3 pencil requests
    assertEquals(3, needs.size());

    // Verify all are pencils
    for (SupplyNeedContribution need : needs) {
      assertEquals("pencil", need.item);
    }

    // Verify calculations:
    // MAES grade 2 with 3 students and quantity 12 = 36 (ticonderoga variant)
    // MAHS HS with 5 students and quantity 24 = 120
    // MAES grade 4 with 2 students and quantity 12 = 24
    for (SupplyNeedContribution need : needs) {
      if (need.school.equals("MAES") && need.grade.equals("2")) {
        assertEquals(36, need.count);
        assertEquals(3, need.studentCount);
      } else if (need.school.equals("MAHS") && need.grade.equals("HS")) {
        assertEquals(120, need.count);
        assertEquals(5, need.studentCount);
      } else if (need.school.equals("MAES") && need.grade.equals("4")) {
        assertEquals(24, need.count);
        assertEquals(2, need.studentCount);
      }
    }
  }

  /**
   * Test filtering by properties. When filtering for ["#2"],
   * we should get pencil requests with #2 property.
   * With quantities of 12 and 24, the expected counts are proportionally larger.
   */
  @Test
  void canGetSupplyNeedsFilteredByProperties() throws IOException {
    Map<String, List<String>> queryParams = new HashMap<>();
    List<String> properties = Arrays.asList("#2");
    queryParams.put(SupplyRequestController.PROPERTIES_KEY, properties);
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParams(SupplyRequestController.PROPERTIES_KEY)).thenReturn(properties);

    supplyRequestController.calculateNeed(ctx);

    verify(ctx).json(supplyNeedContributionCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<SupplyNeedContribution> needs = supplyNeedContributionCaptor.getValue();
    // Should get pencils with #2 and folder with properties
    assertTrue(needs.size() > 0);

    // Verify all returned items have #2 in properties
    for (SupplyNeedContribution need : needs) {
      assertTrue(need.properties != null && Arrays.asList(need.properties).contains("#2"));
    }
  }

  /**
   * Test filtering by grade. When filtering for "2", we should only get
   * supply requests for grade 2.
   */
  @Test
  void canGetSupplyNeedsFilteredByGrade() throws IOException {
    String targetGrade = "2";

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyRequestController.GRADE_KEY, Arrays.asList(targetGrade));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyRequestController.GRADE_KEY)).thenReturn(targetGrade);

    Validation validation = new Validation();
    Validator<String> validator = validation.validator(SupplyRequestController.GRADE_KEY, String.class, targetGrade);
    when(ctx.queryParamAsClass(SupplyRequestController.GRADE_KEY, String.class)).thenReturn(validator);

    supplyRequestController.calculateNeed(ctx);

    verify(ctx).json(supplyNeedContributionCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<SupplyNeedContribution> needs = supplyNeedContributionCaptor.getValue();
    // Should have 2 requests for grade 2: 1 pencil and 1 folder
    assertEquals(2, needs.size());

    // Verify all are for grade 2
    for (SupplyNeedContribution need : needs) {
      assertEquals("2", need.grade);
    }
  }

  /**
   * Test filtering by school. When filtering for "MAES", we should only get
   * supply requests for MAES school.
   */
  @Test
  void canGetSupplyNeedsFilteredBySchool() throws IOException {
    String targetSchool = "MAES";

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyRequestController.SCHOOL_KEY, Arrays.asList(targetSchool));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyRequestController.SCHOOL_KEY)).thenReturn(targetSchool);

    supplyRequestController.calculateNeed(ctx);

    verify(ctx).json(supplyNeedContributionCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<SupplyNeedContribution> needs = supplyNeedContributionCaptor.getValue();
    // Should have 3 requests for MAES
    assertEquals(3, needs.size());

    // Verify all are for MAES
    for (SupplyNeedContribution need : needs) {
      assertEquals("MAES", need.school);
    }
  }

  /**
   * Test combined filters. When filtering for school "MAES" and item "pencil",
   * we should get only pencil requests from MAES school.
   */
  @Test
  void canGetSupplyNeedsWithMultipleFilters() throws IOException {
    String targetSchool = "MAES";
    String targetItem = "pencil";

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyRequestController.SCHOOL_KEY, Arrays.asList(targetSchool));
    queryParams.put(SupplyRequestController.ITEM_KEY, Arrays.asList(targetItem));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyRequestController.SCHOOL_KEY)).thenReturn(targetSchool);
    when(ctx.queryParam(SupplyRequestController.ITEM_KEY)).thenReturn(targetItem);

    supplyRequestController.calculateNeed(ctx);

    verify(ctx).json(supplyNeedContributionCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<SupplyNeedContribution> needs = supplyNeedContributionCaptor.getValue();
    // Should have 2 pencil requests from MAES
    assertEquals(2, needs.size());

    // Verify all are pencils from MAES
    for (SupplyNeedContribution need : needs) {
      assertEquals("pencil", need.item);
      assertEquals("MAES", need.school);
    }
  }

  /**
   * Test that student count is correctly calculated.
   * MAES grade 2 has 3 students, so a quantity of 12 should result in count of 36.
   */
  @Test
  void studentCountIsCalculatedCorrectly() throws IOException {
    String targetSchool = "MAES";
    String targetGrade = "2";
    String targetItem = "pencil";

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyRequestController.SCHOOL_KEY, Arrays.asList(targetSchool));
    queryParams.put(SupplyRequestController.GRADE_KEY, Arrays.asList(targetGrade));
    queryParams.put(SupplyRequestController.ITEM_KEY, Arrays.asList(targetItem));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyRequestController.SCHOOL_KEY)).thenReturn(targetSchool);
    when(ctx.queryParam(SupplyRequestController.ITEM_KEY)).thenReturn(targetItem);
    when(ctx.queryParam(SupplyRequestController.GRADE_KEY)).thenReturn(targetGrade);

    Validation validation = new Validation();
    Validator<String> validator = validation.validator(SupplyRequestController.GRADE_KEY, String.class, targetGrade);
    when(ctx.queryParamAsClass(SupplyRequestController.GRADE_KEY, String.class)).thenReturn(validator);

    supplyRequestController.calculateNeed(ctx);

    verify(ctx).json(supplyNeedContributionCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<SupplyNeedContribution> needs = supplyNeedContributionCaptor.getValue();
    // Should have 1 pencil request for MAES grade 2 (ticonderoga variant)
    assertEquals(1, needs.size());

    // Should have studentCount = 3 (3 students in MAES grade 2)
    assertEquals(3, needs.get(0).studentCount);

    // Should have count = 36 (quantity 12 * 3 students)
    assertEquals(36, needs.get(0).count);
  }

  /**
   * Test the grouped supply needs endpoint. This should group pencils by
   * their item and properties combination, showing how many of each variant
   * we need to purchase. With realistic quantities (12 and 24 per student),
   * the totals will be substantial.
   */
  @Test
  void canGetGroupedSupplyNeeds() throws IOException {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());

    supplyRequestController.calculateNeedGrouped(ctx);

    verify(ctx).json(supplyNeedGroupCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<SupplyNeedGroup> groups = supplyNeedGroupCaptor.getValue();
    // Should have at least 2 groups: pencils with different properties and folders
    assertTrue(groups.size() > 1);

    // Find the pencil groups
    List<SupplyNeedGroup> pencilGroups = new ArrayList<>();
    for (SupplyNeedGroup group : groups) {
      if ("pencil".equals(group.item)) {
        pencilGroups.add(group);
      }
    }

    // Should have multiple pencil groups due to different properties
    assertTrue(pencilGroups.size() > 1);

    // Verify groups have totalCount
    for (SupplyNeedGroup group : groups) {
      assertTrue(group.totalCount > 0);
      assertNotNull(group.supplyRequests);
      assertTrue(group.supplyRequests.size() > 0);
    }
  }

  /**
   * Test that grouped supply needs correctly aggregates counts across
   * multiple supply requests for the same item and properties.
   *
   * For example, pencils with #2 property from MAES grade 2 (quantity 12)
   * and from MAES grade 4 (quantity 12) should be grouped together if
   * they have the same properties.
   */
  @Test
  void groupedSupplyNeedsAggregatesCountsCorrectly() throws IOException {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());

    supplyRequestController.calculateNeedGrouped(ctx);

    verify(ctx).json(supplyNeedGroupCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<SupplyNeedGroup> groups = supplyNeedGroupCaptor.getValue();

    // Find pencil groups
    for (SupplyNeedGroup group : groups) {
      if ("pencil".equals(group.item)) {
        // Group with #2 property only (without ticonderoga)
        if (group.properties != null && group.properties.length == 1 && "#2".equals(group.properties[0])) {
          // This should include pencils from 2 different requests:
          // MAHS HS (quantity 24): 5 students * 24 = 120
          // MAES grade 4 (quantity 12): 2 students * 12 = 24
          // Total = 144
          assertEquals(144, group.totalCount);
          assertEquals(2, group.supplyRequests.size());
        }
      }
    }
  }

  /**
   * Test that requests with null or missing items are excluded from
   * both regular and grouped supply needs.
   */
  @Test
  void nullAndMissingItemsAreExcluded() throws IOException {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());

    supplyRequestController.calculateNeed(ctx);

    verify(ctx).json(supplyNeedContributionCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<SupplyNeedContribution> needs = supplyNeedContributionCaptor.getValue();

    // Verify no null or missing items
    for (SupplyNeedContribution need : needs) {
      assertNotNull(need.item);
      assertTrue(need.item.length() > 0);
    }
  }

  /**
   * Test that no supply needs are returned for grades/schools with no students.
   */
  @Test
  void zeroStudentCountRequestsAreExcluded() throws IOException {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());

    supplyRequestController.calculateNeed(ctx);

    verify(ctx).json(supplyNeedContributionCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    ArrayList<SupplyNeedContribution> needs = supplyNeedContributionCaptor.getValue();

    // Verify no zero student counts
    for (SupplyNeedContribution need : needs) {
      assertTrue(need.studentCount > 0);
      assertTrue(need.count > 0);
    }
  }

  /**
   * Test getting a specific supply request by ID.
   */
  @Test
  void canGetSupplyRequestWithExistentId() throws IOException {
    String id = pencilIdTico.toHexString();
    when(ctx.pathParam("id")).thenReturn(id);

    supplyRequestController.getSupplyRequest(ctx);

    verify(ctx).status(HttpStatus.OK);
  }

  /**
   * Test that requesting a supply request with a bad ID throws BadRequestResponse.
   */
  @Test
  void getSupplyRequestWithBadId() throws IOException {
    when(ctx.pathParam("id")).thenReturn("bad");

    assertThrows(BadRequestResponse.class, () -> {
      supplyRequestController.getSupplyRequest(ctx);
    });
  }

  /**
   * Test that requesting a non-existent supply request ID throws NotFoundResponse.
   */
  @Test
  void getSupplyRequestWithNonexistentId() throws IOException {
    String id = new ObjectId().toHexString();
    when(ctx.pathParam("id")).thenReturn(id);

    assertThrows(NotFoundResponse.class, () -> {
      supplyRequestController.getSupplyRequest(ctx);
    });
  }

  /**
   * Test getting all supply requests (no filters).
   */
  @Test
  void canGetAllSupplyRequests() throws IOException {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());
    when(ctx.queryParam("sortby")).thenReturn(null);
    when(ctx.queryParam("sortorder")).thenReturn(null);

    supplyRequestController.getSupplyRequests(ctx);

    verify(ctx).json(any());
    verify(ctx).status(HttpStatus.OK);
  }

  /**
   * Test filtering supply requests by school using getSupplyRequests.
   */
  @Test
  void canFilterSupplyRequestsBySchool() throws IOException {
    String targetSchool = "MAES";

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyRequestController.SCHOOL_KEY, Arrays.asList(targetSchool));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyRequestController.SCHOOL_KEY)).thenReturn(targetSchool);
    when(ctx.queryParam("sortby")).thenReturn(null);
    when(ctx.queryParam("sortorder")).thenReturn(null);

    supplyRequestController.getSupplyRequests(ctx);

    verify(ctx).json(any());
    verify(ctx).status(HttpStatus.OK);
  }

  /**
   * Test filtering supply requests by grade using getSupplyRequests.
   */
  @Test
  void canFilterSupplyRequestsByGrade() throws IOException {
    String targetGrade = "2";

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyRequestController.GRADE_KEY, Arrays.asList(targetGrade));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyRequestController.GRADE_KEY)).thenReturn(targetGrade);
    when(ctx.queryParam("sortby")).thenReturn(null);
    when(ctx.queryParam("sortorder")).thenReturn(null);

    Validation validation = new Validation();
    Validator<String> validator = validation.validator(SupplyRequestController.GRADE_KEY, String.class, targetGrade);
    when(ctx.queryParamAsClass(SupplyRequestController.GRADE_KEY, String.class)).thenReturn(validator);

    supplyRequestController.getSupplyRequests(ctx);

    verify(ctx).json(any());
    verify(ctx).status(HttpStatus.OK);
  }

  /**
   * Test filtering supply requests by item using getSupplyRequests.
   */
  @Test
  void canFilterSupplyRequestsByItemOnGetRequests() throws IOException {
    String targetItem = "pencil";

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyRequestController.ITEM_KEY, Arrays.asList(targetItem));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyRequestController.ITEM_KEY)).thenReturn(targetItem);
    when(ctx.queryParam("sortby")).thenReturn(null);
    when(ctx.queryParam("sortorder")).thenReturn(null);

    supplyRequestController.getSupplyRequests(ctx);

    verify(ctx).json(any());
    verify(ctx).status(HttpStatus.OK);
  }

  /**
   * Test filtering supply requests by properties using getSupplyRequests.
   */
  @Test
  void canFilterSupplyRequestsByPropertiesOnGetRequests() throws IOException {
    List<String> targetProperties = Arrays.asList("#2");

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyRequestController.PROPERTIES_KEY, targetProperties);
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParams(SupplyRequestController.PROPERTIES_KEY)).thenReturn(targetProperties);
    when(ctx.queryParam("sortby")).thenReturn(null);
    when(ctx.queryParam("sortorder")).thenReturn(null);

    supplyRequestController.getSupplyRequests(ctx);

    verify(ctx).json(any());
    verify(ctx).status(HttpStatus.OK);
  }

  /**
   * Test filtering supply requests by school and grade using getSupplyRequests.
   */
  @Test
  void canFilterSupplyRequestsBySchoolAndGrade() throws IOException {
    String targetSchool = "MAES";
    String targetGrade = "2";

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyRequestController.SCHOOL_KEY, Arrays.asList(targetSchool));
    queryParams.put(SupplyRequestController.GRADE_KEY, Arrays.asList(targetGrade));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyRequestController.SCHOOL_KEY)).thenReturn(targetSchool);
    when(ctx.queryParam(SupplyRequestController.GRADE_KEY)).thenReturn(targetGrade);
    when(ctx.queryParam("sortby")).thenReturn(null);
    when(ctx.queryParam("sortorder")).thenReturn(null);

    Validation validation = new Validation();
    Validator<String> validator = validation.validator(SupplyRequestController.GRADE_KEY, String.class, targetGrade);
    when(ctx.queryParamAsClass(SupplyRequestController.GRADE_KEY, String.class)).thenReturn(validator);

    supplyRequestController.getSupplyRequests(ctx);

    verify(ctx).json(any());
    verify(ctx).status(HttpStatus.OK);
  }

  /**
   * Test filtering supply requests by school and item using getSupplyRequests.
   */
  @Test
  void canFilterSupplyRequestsBySchoolAndItem() throws IOException {
    String targetSchool = "MAES";
    String targetItem = "pencil";

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyRequestController.SCHOOL_KEY, Arrays.asList(targetSchool));
    queryParams.put(SupplyRequestController.ITEM_KEY, Arrays.asList(targetItem));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyRequestController.SCHOOL_KEY)).thenReturn(targetSchool);
    when(ctx.queryParam(SupplyRequestController.ITEM_KEY)).thenReturn(targetItem);
    when(ctx.queryParam("sortby")).thenReturn(null);
    when(ctx.queryParam("sortorder")).thenReturn(null);

    supplyRequestController.getSupplyRequests(ctx);

    verify(ctx).json(any());
    verify(ctx).status(HttpStatus.OK);
  }

  /**
   * Test filtering supply requests by item and properties using getSupplyRequests.
   */
  @Test
  void canFilterSupplyRequestsByItemAndProperties() throws IOException {
    String targetItem = "pencil";
    List<String> targetProperties = Arrays.asList("#2");

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyRequestController.ITEM_KEY, Arrays.asList(targetItem));
    queryParams.put(SupplyRequestController.PROPERTIES_KEY, targetProperties);
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyRequestController.ITEM_KEY)).thenReturn(targetItem);
    when(ctx.queryParams(SupplyRequestController.PROPERTIES_KEY)).thenReturn(targetProperties);
    when(ctx.queryParam("sortby")).thenReturn(null);
    when(ctx.queryParam("sortorder")).thenReturn(null);

    supplyRequestController.getSupplyRequests(ctx);

    verify(ctx).json(any());
    verify(ctx).status(HttpStatus.OK);
  }

  /**
   * Test filtering supply requests by all four filter parameters.
   */
  @Test
  void canFilterSupplyRequestsByAllParameters() throws IOException {
    String targetSchool = "MAES";
    String targetGrade = "2";
    String targetItem = "pencil";
    List<String> targetProperties = Arrays.asList("ticonderoga");

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyRequestController.SCHOOL_KEY, Arrays.asList(targetSchool));
    queryParams.put(SupplyRequestController.GRADE_KEY, Arrays.asList(targetGrade));
    queryParams.put(SupplyRequestController.ITEM_KEY, Arrays.asList(targetItem));
    queryParams.put(SupplyRequestController.PROPERTIES_KEY, targetProperties);
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyRequestController.SCHOOL_KEY)).thenReturn(targetSchool);
    when(ctx.queryParam(SupplyRequestController.GRADE_KEY)).thenReturn(targetGrade);
    when(ctx.queryParam(SupplyRequestController.ITEM_KEY)).thenReturn(targetItem);
    when(ctx.queryParams(SupplyRequestController.PROPERTIES_KEY)).thenReturn(targetProperties);
    when(ctx.queryParam("sortby")).thenReturn(null);
    when(ctx.queryParam("sortorder")).thenReturn(null);

    Validation validation = new Validation();
    Validator<String> validator = validation.validator(SupplyRequestController.GRADE_KEY, String.class, targetGrade);
    when(ctx.queryParamAsClass(SupplyRequestController.GRADE_KEY, String.class)).thenReturn(validator);

    supplyRequestController.getSupplyRequests(ctx);

    verify(ctx).json(any());
    verify(ctx).status(HttpStatus.OK);
  }

  /**
   * Test sorting supply requests by item in ascending order.
   */
  @Test
  void canSortSupplyRequestsByItemAscending() throws IOException {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());
    when(ctx.queryParam("sortby")).thenReturn("item");
    when(ctx.queryParam("sortorder")).thenReturn("asc");

    supplyRequestController.getSupplyRequests(ctx);

    verify(ctx).json(any());
    verify(ctx).status(HttpStatus.OK);
  }

  /**
   * Test sorting supply requests by item in descending order.
   */
  @Test
  void canSortSupplyRequestsByItemDescending() throws IOException {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());
    when(ctx.queryParam("sortby")).thenReturn("item");
    when(ctx.queryParam("sortorder")).thenReturn("desc");

    supplyRequestController.getSupplyRequests(ctx);

    verify(ctx).json(any());
    verify(ctx).status(HttpStatus.OK);
  }

  /**
   * Test sorting supply requests by grade in ascending order.
   */
  @Test
  void canSortSupplyRequestsByGradeAscending() throws IOException {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());
    when(ctx.queryParam("sortby")).thenReturn("grade");
    when(ctx.queryParam("sortorder")).thenReturn("asc");

    supplyRequestController.getSupplyRequests(ctx);

    verify(ctx).json(any());
    verify(ctx).status(HttpStatus.OK);
  }

  /**
   * Test sorting supply requests by grade in descending order.
   */
  @Test
  void canSortSupplyRequestsByGradeDescending() throws IOException {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());
    when(ctx.queryParam("sortby")).thenReturn("grade");
    when(ctx.queryParam("sortorder")).thenReturn("desc");

    supplyRequestController.getSupplyRequests(ctx);

    verify(ctx).json(any());
    verify(ctx).status(HttpStatus.OK);
  }

  /**
   * Test sorting supply requests by school in ascending order.
   */
  @Test
  void canSortSupplyRequestsBySchoolAscending() throws IOException {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());
    when(ctx.queryParam("sortby")).thenReturn("school");
    when(ctx.queryParam("sortorder")).thenReturn("asc");

    supplyRequestController.getSupplyRequests(ctx);

    verify(ctx).json(any());
    verify(ctx).status(HttpStatus.OK);
  }

  /**
   * Test combining filter by item with sorting by grade.
   */
  @Test
  void canFilterByItemAndSortByGrade() throws IOException {
    String targetItem = "pencil";

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyRequestController.ITEM_KEY, Arrays.asList(targetItem));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyRequestController.ITEM_KEY)).thenReturn(targetItem);
    when(ctx.queryParam("sortby")).thenReturn("grade");
    when(ctx.queryParam("sortorder")).thenReturn("asc");

    supplyRequestController.getSupplyRequests(ctx);

    verify(ctx).json(any());
    verify(ctx).status(HttpStatus.OK);
  }

  /**
   * Test combining filter by school with sorting by item.
   */
  @Test
  void canFilterBySchoolAndSortByItem() throws IOException {
    String targetSchool = "MAES";

    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(SupplyRequestController.SCHOOL_KEY, Arrays.asList(targetSchool));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(SupplyRequestController.SCHOOL_KEY)).thenReturn(targetSchool);
    when(ctx.queryParam("sortby")).thenReturn("item");
    when(ctx.queryParam("sortorder")).thenReturn("asc");

    supplyRequestController.getSupplyRequests(ctx);

    verify(ctx).json(any());
    verify(ctx).status(HttpStatus.OK);
  }
}
