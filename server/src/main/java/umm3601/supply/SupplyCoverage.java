package umm3601.supply;

@SuppressWarnings({"VisibilityModifier"})
public class SupplyCoverage {
  public String item;
  public String[] requestedProperties;
  public int neededQuantity;

  public String bestSupplyId;
  public String bestSupplyDescription;
  public String[] bestSupplyProperties;
  public int onHandQuantity;
  public int allocatedQuantity;
  public int remainingQuantityAfterAllocation;

  public int extraPropertiesCount;
  public int shortageQuantity;

  // Indicates how many grouped needs are competing for the same matched supply.
  public int matchedRequestCount;
  // Sum of all need quantities that map to the same matched supply.
  public int totalNeededAgainstMatchedSupply;
}
