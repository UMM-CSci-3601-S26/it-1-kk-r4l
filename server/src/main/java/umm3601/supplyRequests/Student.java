package umm3601.supplyRequests;


import org.mongojack.Id;
import org.mongojack.ObjectId;

@SuppressWarnings({"VisibilityModifier"})
public class Student {

  @ObjectId @Id
  // By default Java field names shouldn't start with underscores.
  // Here, though, we *have* to use the name `_id` to match the
  // name of the field as used by MongoDB.
  @SuppressWarnings({"MemberName"})
  public String _id;

  public String first;
  public String last;
  public String school;
  public String grade;

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Student)) {
      return false;
    }
    Student other = (Student) obj;
    return _id.equals(other._id);
  }

  @Override
  public int hashCode() {
    // This means that equal Students will hash the same, which is good.
    return _id.hashCode();
  }

  // Having some kind of `toString()` allows us to print `Student`s,
  // which can be useful/necessary in error handling. This only
  // returns the name, but it could be extended to return more or
  // all of the fields combined into a single string.
  //
  // The other option would be to return `_id`, but that can be
  // `null` if we're trying to add a new `Student` to the database
  // that doesn't yet have an `_id`, so returning `name` seemed
  // the better bet.
  @Override
  public String toString() {
    return first + " " + last;
  }
}
