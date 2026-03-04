package umm3601.supplyRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StudentSpec {

  private static final String FAKE_ID_STRING_1 = "fakeIdOne";
  private static final String FAKE_ID_STRING_2 = "fakeIdTwo";

  private Student student1;
  private Student student2;

  @BeforeEach
  void setupEach() {
    student1 = new Student();
    student2 = new Student();
  }

  @Test
  void suppliesWithEqualIdAreEqual() {
    student1._id = FAKE_ID_STRING_1;
    student2._id = FAKE_ID_STRING_1;

    assertTrue(student1.equals(student2));
  }

  @Test
  void suppliesWithDifferentIdAreNotEqual() {
    student1._id = FAKE_ID_STRING_1;
    student2._id = FAKE_ID_STRING_2;

    assertFalse(student1.equals(student2));
  }

  @Test
  void hashCodesAreBasedOnId() {
    student1._id = FAKE_ID_STRING_1;
    student2._id = FAKE_ID_STRING_1;

    assertTrue(student1.hashCode() == student2.hashCode());
  }

  @SuppressWarnings("unlikely-arg-type")
  @Test
  void suppliesAreNotEqualToOtherKindsOfThings() {
    student1._id = FAKE_ID_STRING_1;
    // a student is not equal to its id even though id is used for checking equality
    assertFalse(student1.equals(FAKE_ID_STRING_1));
  }
}
