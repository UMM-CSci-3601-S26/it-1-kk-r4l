package umm3601.supply;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupplySpec {

  private static final String FAKE_ID_STRING_1 = "fakeIdOne";
  private static final String FAKE_ID_STRING_2 = "fakeIdTwo";

  private Supply supply1;
  private Supply supply2;

  @BeforeEach
  void setupEach() {
    supply1 = new Supply();
    supply2 = new Supply();
  }

  @Test
  void suppliesWithEqualIdAreEqual() {
    supply1._id = FAKE_ID_STRING_1;
    supply2._id = FAKE_ID_STRING_1;

    assertTrue(supply1.equals(supply2));
  }

  @Test
  void suppliesWithDifferentIdAreNotEqual() {
    supply1._id = FAKE_ID_STRING_1;
    supply2._id = FAKE_ID_STRING_2;

    assertFalse(supply1.equals(supply2));
  }

  @Test
  void hashCodesAreBasedOnId() {
    supply1._id = FAKE_ID_STRING_1;
    supply2._id = FAKE_ID_STRING_1;

    assertTrue(supply1.hashCode() == supply2.hashCode());
  }

  @SuppressWarnings("unlikely-arg-type")
  @Test
  void suppliesAreNotEqualToOtherKindsOfThings() {
    supply1._id = FAKE_ID_STRING_1;
    // a supply is not equal to its id even though id is used for checking equality
    assertFalse(supply1.equals(FAKE_ID_STRING_1));
  }
}
