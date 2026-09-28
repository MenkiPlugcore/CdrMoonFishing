package id.menki.cdrmoonfishing.item;

public record FishItemDefinition(
        FishItemProvider provider,
        String itemsAdderId,
        String mmoItemsType,
        String mmoItemsId,
        boolean preserveProviderName,
        boolean preserveProviderLore
) {
    public static FishItemDefinition vanilla() {
        return new FishItemDefinition(FishItemProvider.VANILLA, null, null, null, false, false);
    }
}
