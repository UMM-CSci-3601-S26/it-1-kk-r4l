package umm3601.supplyRequests;

import org.mongojack.Id;
import org.mongojack.ObjectId;

@SuppressWarnings({"VisibilityModifier"})
public class SupplyRequest {

  @ObjectId @Id
  // By default Java field names shouldn't start with underscores.
  // Here, though, we *have* to use the name `_id` to match the
  // name of the field as used by MongoDB.
  @SuppressWarnings({"MemberName"})
  public String _id;

  public String school;
  public String grade;
  public String teacher;
  public String description;
  public String item;
  public String[] properties;
  public int quantity;
  public String notes;

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SupplyRequest)) {
      return false;
    }
    SupplyRequest other = (SupplyRequest) obj;
    return _id.equals(other._id);
  }

  @Override
  public int hashCode() {
    // This means that equal SupplyRequests will hash the same, which is good.
    return _id.hashCode();
  }

  @Override
  public String toString() {
    return description;
  }
}
