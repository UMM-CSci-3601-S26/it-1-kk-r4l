package umm3601.supply;

import org.mongojack.Id;
import org.mongojack.ObjectId;

@SuppressWarnings({"VisibilityModifier"})
public class Supply {

  @ObjectId @Id
  // By default Java field names shouldn't start with underscores.
  // Here, though, we *have* to use the name `_id` to match the
  // name of the field as used by MongoDB.
  @SuppressWarnings({"MemberName"})
  public String _id;

  public String description;
  public String item;
  public String[] properties;
  public int quantity;

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Supply)) {
      return false;
    }
    Supply other = (Supply) obj;
    return _id.equals(other._id);
  }

  @Override
  public int hashCode() {
    // This means that equal supplies will hash the same, which is good.
    return _id.hashCode();
  }

  // Having some kind of `toString()` allows us to print `Supply`s,
  // which can be useful/necessary in error handling. This only
  // returns the name, but it could be extended to return more or
  // all of the fields combined into a single string.
  //
  // The other option would be to return `_id`, but that can be
  // `null` if we're trying to add a new `Supply` to the database
  // that doesn't yet have an `_id`, so returning `description` seemed
  // the better bet.
  @Override
  public String toString() {
    return description;
  }
}
