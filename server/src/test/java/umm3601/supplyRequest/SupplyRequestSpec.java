package umm3601.supplyRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupplyRequestSpec {

  private static final String FAKE_ID_STRING_1 = "fakeIdOne";
  private static final String FAKE_ID_STRING_2 = "fakeIdTwo";

  private SupplyRequest supplyRequest1;
  private SupplyRequest supplyRequest2;

  @BeforeEach
  void setupEach() {
    supplyRequest1 = new SupplyRequest();
    supplyRequest2 = new SupplyRequest();
  }

  @Test
  void supplyRequestsWithEqualIdAreEqual() {
    supplyRequest1._id = FAKE_ID_STRING_1;
    supplyRequest2._id = FAKE_ID_STRING_1;

    assertTrue(supplyRequest1.equals(supplyRequest2));
  }

  @Test
  void supplyRequestsWithDifferentIdAreNotEqual() {
    supplyRequest1._id = FAKE_ID_STRING_1;
    supplyRequest2._id = FAKE_ID_STRING_2;

    assertFalse(supplyRequest1.equals(supplyRequest2));
  }

  @Test
  void hashCodesAreBasedOnId() {
    supplyRequest1._id = FAKE_ID_STRING_1;
    supplyRequest2._id = FAKE_ID_STRING_1;

    assertTrue(supplyRequest1.hashCode() == supplyRequest2.hashCode());
  }

  @SuppressWarnings("unlikely-arg-type")
  @Test
  void supplyRequestsAreNotEqualToOtherKindsOfThings() {
    supplyRequest1._id = FAKE_ID_STRING_1;
    // a supplyRequest is not equal to its id even though id is used for checking equality
    assertFalse(supplyRequest1.equals(FAKE_ID_STRING_1));
  }
}
