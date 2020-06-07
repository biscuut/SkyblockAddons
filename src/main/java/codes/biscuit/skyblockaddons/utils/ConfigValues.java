package codes.biscuit.skyblockaddons.utils;

import codes.biscuit.skyblockaddons.SkyblockAddons;
import codes.biscuit.skyblockaddons.core.Feature;
import codes.biscuit.skyblockaddons.utils.discord.DiscordStatus;
import codes.biscuit.skyblockaddons.utils.nifty.ChatFormatting;
import com.google.gson.*;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.text.WordUtils;

import java.awt.*;
import java.awt.geom.Point2D;
import java.beans.Introspector;
import java.io.*;
import java.lang.reflect.Method;
import java.util.List;
import java.util.*;

public class ConfigValues {

    private static final int CONFIG_VERSION = 7;

    private final static float DEFAULT_GUI_SCALE = normalizeValueNoStep(1);
    private final static float GUI_SCALE_MINIMUM = 0.5F;
    private final static float GUI_SCALE_MAXIMUM = 5;

    private SkyblockAddons main;

    private File settingsConfigFile;
    private JsonObject settingsConfig = new JsonObject();
    @Getter @Setter private JsonObject languageConfig = new JsonObject();

    @Getter private Set<Feature> disabledFeatures = EnumSet.noneOf(Feature.class);
    private Map<Feature, Integer> colors = new HashMap<>();
    private Map<Feature, Float> guiScales = new EnumMap<>(Feature.class);
    private Map<Feature, CoordsPair> barSizes = new EnumMap<>(Feature.class);
    private MutableInt warningSeconds = new MutableInt(4);
    private Map<Feature, CoordsPair> coordinates = new EnumMap<>(Feature.class);
    private Map<Feature, EnumUtils.AnchorPoint> anchorPoints = new EnumMap<>(Feature.class);
    private MutableObject<Language> language = new MutableObject<>(Language.ENGLISH);
    private MutableObject<EnumUtils.BackpackStyle> backpackStyle = new MutableObject<>(EnumUtils.BackpackStyle.GUI);
    private MutableObject<EnumUtils.PowerOrbDisplayStyle> powerOrbDisplayStyle = new MutableObject<>(EnumUtils.PowerOrbDisplayStyle.COMPACT);
    private MutableObject<EnumUtils.TextStyle> textStyle = new MutableObject<>(EnumUtils.TextStyle.STYLE_ONE);
    private Map<String, Set<Integer>> profileLockedSlots = new HashMap<>();
    @Getter private Set<Feature> chromaFeatures = new HashSet<>();
    private MutableFloat chromaSpeed = new MutableFloat(0.19354838F); // 2.0
    private MutableObject<EnumUtils.ChromaMode> chromaMode = new MutableObject<>(EnumUtils.ChromaMode.FADE);
    private MutableFloat chromaFadeWidth = new MutableFloat(0.22580644F); // 10° Hue
    private MutableObject<DiscordStatus> discordDetails =  new MutableObject<>(DiscordStatus.LOCATION);
    private MutableObject<DiscordStatus> discordStatus = new MutableObject<>(DiscordStatus.AUTO_STATUS);
    private MutableObject<DiscordStatus> discordAutoDefault = new MutableObject<>(DiscordStatus.NONE);
    @Getter private List<String> discordCustomStatuses = new ArrayList<>();

    public ConfigValues(SkyblockAddons main, File settingsConfigFile) {
        this.main = main;
        this.settingsConfigFile = settingsConfigFile;
    }

    public void loadConfig() {
        if (settingsConfigFile.exists()) {
            try {
                FileReader reader = new FileReader(settingsConfigFile);
                JsonElement fileElement = new JsonParser().parse(reader);

                if (fileElement == null || fileElement.isJsonNull()) {
                    throw new JsonParseException("File is null!");
                }
                settingsConfig = fileElement.getAsJsonObject();
            } catch (JsonParseException | IllegalStateException | IOException ex) {
                ex.printStackTrace();
                System.out.println("SkyblockAddons: There was an error loading the config. Resetting all settings to default.");
                addDefaultsAndSave();
                return;
            }

            deserializeFeatureSetFromID(disabledFeatures, "disabledFeatures");
            deserializeStringIntSetMap(profileLockedSlots, "profileLockedSlots");
            deserializeNumber(warningSeconds, "warningSeconds", int.class);

            try {
                if (settingsConfig.has("language")) {
                    String languageKey = settingsConfig.get("language").getAsString();
                    Language configLanguage = Language.getFromPath(languageKey);
                    if (configLanguage != null) {
                        language.setValue(configLanguage);
                    }
                }
            } catch (Exception ex) {
                SkyblockAddons.getInstance().getLogger().error("Failed to deserialize path: language");
                ex.printStackTrace();
            }

            deserializeEnumValueFromOrdinal(backpackStyle, "backpackStyle");
            deserializeEnumValueFromOrdinal(powerOrbDisplayStyle, "powerOrbStyle");
            deserializeEnumEnumMapFromIDS(anchorPoints, "anchorPoints", Feature.class, EnumUtils.AnchorPoint.class);
            deserializeEnumNumberMapFromID(guiScales, "guiScales", Feature.class, float.class);

            try {
                for (Feature feature : Feature.getGuiFeatures()) { // TODO Legacy format from 1.3.4, remove in the future.
                    String property = Introspector.decapitalize(WordUtils.capitalizeFully(feature.toString().replace("_", " "))).replace(" ", "");
                    String x = property+"X";
                    String y = property+"Y";
                    if (settingsConfig.has(x)) {
                        coordinates.put(feature, new CoordsPair(settingsConfig.get(x).getAsInt(), settingsConfig.get(y).getAsInt()));
                    }
                }
            } catch (Exception ex) {
                SkyblockAddons.getInstance().getLogger().error("Failed to deserialize path: coordinates (legacy)");
                ex.printStackTrace();
            }

            deserializeFeatureCoordsMapFromID(coordinates, "guiPositions");
            deserializeFeatureCoordsMapFromID(barSizes, "barSizes");

            if (settingsConfig.has("featureColors")) { // TODO Legacy format from 1.3.4, remove in the future.
                try {
                    for (Map.Entry<String, JsonElement> element : settingsConfig.getAsJsonObject("featureColors").entrySet()) {
                        Feature feature = Feature.fromId(Integer.parseInt(element.getKey()));
                        if (feature != null) {
                            ChatFormatting chatFormatting = ChatFormatting.values()[element.getValue().getAsInt()];
                            if (chatFormatting.isColor() && chatFormatting != ChatFormatting.RED) { // Red is default, no need to set it.
                                colors.put(feature, chatFormatting.getRGB());
                            }
                        }
                    }
                } catch (Exception ex) {
                    SkyblockAddons.getInstance().getLogger().error("Failed to deserialize path: featureColors");
                    ex.printStackTrace();
                }
            } else {
                deserializeEnumNumberMapFromID(colors, "colors", Feature.class, int.class);
            }

            deserializeEnumValueFromOrdinal(textStyle, "textStyle");
            deserializeFeatureSetFromID(chromaFeatures, "chromaFeatures");
            deserializeNumber(chromaSpeed, "chromaSpeed", float.class);
            deserializeEnumValueFromOrdinal(chromaMode, "chromaMode");
            deserializeNumber(chromaFadeWidth, "chromaFadeWidth", float.class);
            deserializeEnumValueFromOrdinal(discordStatus, "discordStatus");
            deserializeEnumValueFromOrdinal(discordDetails, "discordDetails");
            deserializeEnumValueFromOrdinal(discordAutoDefault, "discordAutoDefault");
            deserializeStringCollection(discordCustomStatuses, "discordCustomStatuses");

            int configVersion;
            if (settingsConfig.has("configVersion")) {
                configVersion = settingsConfig.get("configVersion").getAsInt();
            } else {
                configVersion = ConfigValues.CONFIG_VERSION;
            }
            if (configVersion <= 5) {
                disabledFeatures.add(Feature.REPLACE_ROMAN_NUMERALS_WITH_NUMBERS);
            } else if (configVersion <= 6) {
                putDefaultBarSizes();
                for (Map.Entry<Feature, CoordsPair> entry : coordinates.entrySet()) {
                    if (getAnchorPoint(entry.getKey()) == EnumUtils.AnchorPoint.BOTTOM_MIDDLE) {
                        CoordsPair coords = entry.getValue();
                        coords.setX(coords.getX()-91);
                        coords.setY(coords.getY()-39);
                    }
                }
            }

            int lastFeatureID;
            if (settingsConfig.has("lastFeatureID")) {
                lastFeatureID = settingsConfig.get("lastFeatureID").getAsInt();
            } else {
                // This system was added after this feature.
                lastFeatureID = Feature.SKYBLOCK_ADDONS_BUTTON_IN_PAUSE_MENU.getId();
            }
            // This will go through every feature, and if they are new features that didn't exist before
            // that should be disabled by default, and their coordinates are default, this will disable those features.
            for (Feature feature : Feature.values()) {
                if (feature.getId() > lastFeatureID && feature.isDefaultDisabled() && featureCoordinatesAreDefault(feature)) {
                    this.getDisabledFeatures().add(feature);
                }
            }
        } else {
            addDefaultsAndSave();
        }
        main.getUtils().loadLanguageFile(true);
    }

    private void addDefaultsAndSave() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            if (mc.getLanguageManager() != null && mc.getLanguageManager().getCurrentLanguage().getLanguageCode() != null) {
                String minecraftLanguage = Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode().toLowerCase();
                Language configLanguage = Language.getFromPath(minecraftLanguage);
                if (configLanguage != null) { // Check if we have the exact locale they are using for Minecraft
                    language.setValue(configLanguage);
                } else { // Check if we at least have the same language (different locale)
                    String languageCode = minecraftLanguage.split("_")[0];
                    for (Language loopLanguage : Language.values()) {
                        String loopLanguageCode = loopLanguage.getPath().split("_")[0];
                        if (loopLanguageCode.equals(languageCode)) {
                            language.setValue(loopLanguage);
                            break;
                        }
                    }
                }
            }
        }

        for (Feature feature : Feature.values()) {
            ChatFormatting color = feature.getDefaultColor();
            if (color != null) {
                colors.put(feature, color.getRGB());
            }
            if (feature.isDefaultDisabled()) {
                disabledFeatures.add(feature);
            }
        }

        setAllCoordinatesToDefault();
        putDefaultBarSizes();
        saveConfig();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void saveConfig() {
        settingsConfig = new JsonObject();
        try {
            settingsConfigFile.createNewFile();
            FileWriter writer = new FileWriter(settingsConfigFile);
            BufferedWriter bufferedWriter = new BufferedWriter(writer);

            JsonArray jsonArray = new JsonArray();
            for (Feature element : disabledFeatures) {
                jsonArray.add(new GsonBuilder().create().toJsonTree(element.getId()));
            }
            settingsConfig.add("disabledFeatures", jsonArray);

            JsonObject profileSlotsObject = new JsonObject();
            for (Map.Entry<String, Set<Integer>> entry : profileLockedSlots.entrySet()) {
                JsonArray lockedSlots = new JsonArray();
                for (int slot : entry.getValue()) {
                    lockedSlots.add(new GsonBuilder().create().toJsonTree(slot));
                }
                profileSlotsObject.add(entry.getKey(), lockedSlots);
            }
            settingsConfig.add("profileLockedSlots", profileSlotsObject);

            JsonObject anchorObject = new JsonObject();
            for (Feature feature : Feature.getGuiFeatures()) {
                anchorObject.addProperty(String.valueOf(feature.getId()), getAnchorPoint(feature).getId());
            }
            settingsConfig.add("anchorPoints", anchorObject);

            JsonObject scalesObject = new JsonObject();
            for (Feature feature : guiScales.keySet()) {
                scalesObject.addProperty(String.valueOf(feature.getId()), guiScales.get(feature));
            }
            settingsConfig.add("guiScales", scalesObject);

            JsonObject colorsObject = new JsonObject();
            for (Feature feature : colors.keySet()) {
                int featureColor = colors.get(feature);
                if (featureColor != ChatFormatting.RED.getRGB()) { // Red is default, no need to save it!
                    colorsObject.addProperty(String.valueOf(feature.getId()), colors.get(feature));
                }
            }
            settingsConfig.add("colors", colorsObject);

            JsonObject coordinatesObject = new JsonObject();
            for (Feature feature : coordinates.keySet()) {
                JsonArray coordinatesArray = new JsonArray();
                coordinatesArray.add(new GsonBuilder().create().toJsonTree(coordinates.get(feature).getX()));
                coordinatesArray.add(new GsonBuilder().create().toJsonTree(coordinates.get(feature).getY()));
                coordinatesObject.add(String.valueOf(feature.getId()), coordinatesArray);
            }
            settingsConfig.add("guiPositions", coordinatesObject);

            JsonObject barSizesObject = new JsonObject();
            for (Feature feature : barSizes.keySet()) {
                JsonArray sizesArray = new JsonArray();
                sizesArray.add(new GsonBuilder().create().toJsonTree(barSizes.get(feature).getX()));
                sizesArray.add(new GsonBuilder().create().toJsonTree(barSizes.get(feature).getY()));
                barSizesObject.add(String.valueOf(feature.getId()), sizesArray);
            }
            settingsConfig.add("barSizes", barSizesObject);

            settingsConfig.addProperty("warningSeconds", warningSeconds);

            settingsConfig.addProperty("textStyle", textStyle.getValue().ordinal());
            settingsConfig.addProperty("language", language.getValue().getPath());
            settingsConfig.addProperty("backpackStyle", backpackStyle.getValue().ordinal());
            settingsConfig.addProperty("powerOrbStyle", powerOrbDisplayStyle.getValue().ordinal());

            JsonArray chromaFeaturesArray = new JsonArray();
            for (Feature feature : chromaFeatures) {
                chromaFeaturesArray.add(new GsonBuilder().create().toJsonTree(feature.getId()));
            }
            settingsConfig.add("chromaFeatures", chromaFeaturesArray);
            settingsConfig.addProperty("chromaSpeed", chromaSpeed);
            settingsConfig.addProperty("chromaMode", chromaMode.getValue().ordinal());
            settingsConfig.addProperty("chromaFadeWidth", chromaFadeWidth);

            settingsConfig.addProperty("discordStatus", discordStatus.getValue().ordinal());
            settingsConfig.addProperty("discordDetails", discordDetails.getValue().ordinal());
            settingsConfig.addProperty("discordAutoDefault", discordAutoDefault.getValue().ordinal());

            JsonArray discordCustomStatusesArray = new JsonArray();
            for (String string : discordCustomStatuses) {
                discordCustomStatusesArray.add(new GsonBuilder().create().toJsonTree(string));
            }
            settingsConfig.add("discordCustomStatuses", discordCustomStatusesArray);

            settingsConfig.addProperty("configVersion", CONFIG_VERSION);
            int largestFeatureID = 0;
            for (Feature feature : Feature.values()) {
                if (feature.getId() > largestFeatureID) largestFeatureID = feature.getId();
            }
            settingsConfig.addProperty("lastFeatureID", largestFeatureID);

            bufferedWriter.write(settingsConfig.toString());
            bufferedWriter.close();
            writer.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("SkyblockAddons: An error occurred while attempting to save the config!");
        }
    }


    private void deserializeFeatureSetFromID(Collection<Feature> collection, String path) {
        try {
            if (settingsConfig.has(path)) {
                for (JsonElement element : settingsConfig.getAsJsonArray(path)) {
                    Feature feature = Feature.fromId(element.getAsInt());
                    if (feature != null) {
                        collection.add(feature);
                    }
                }
            }
        } catch (Exception ex) {
            SkyblockAddons.getInstance().getLogger().error("Failed to deserialize path: "+ path);
            ex.printStackTrace();
        }
    }

    private void deserializeStringCollection(Collection<String> collection, String path) {
        try {
            if (settingsConfig.has(path)) {
                for (JsonElement element : settingsConfig.getAsJsonArray(path)) {
                    String string = element.getAsString();
                    if (string != null) {
                        collection.add(string);
                    }
                }
            }
        } catch (Exception ex) {
            SkyblockAddons.getInstance().getLogger().error("Failed to deserialize path: "+ path);
            ex.printStackTrace();
        }
    }

    private void deserializeStringIntSetMap(Map<String, Set<Integer>> map, String path) {
        try {
            if (settingsConfig.has(path)) {
                JsonObject profileSlotsObject = settingsConfig.getAsJsonObject(path);
                for (Map.Entry<String, JsonElement> entry : profileSlotsObject.entrySet()) {
                    Set<Integer> slots = new HashSet<>();
                    for (JsonElement element : entry.getValue().getAsJsonArray()) {
                        slots.add(element.getAsInt());
                    }
                    map.put(entry.getKey(), slots);
                }
            }
        } catch (Exception ex) {
            SkyblockAddons.getInstance().getLogger().error("Failed to deserialize path: "+ path);
            ex.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum<?>, F extends Enum<?>> void deserializeEnumEnumMapFromIDS(Map<E, F> map, String path, Class<E> keyClass, Class<F> valueClass) {
        try {
            if (settingsConfig.has(path)) {
                for (Map.Entry<String, JsonElement> element : settingsConfig.getAsJsonObject(path).entrySet()) {

                    Method fromId = keyClass.getDeclaredMethod("fromId", int.class);
                    E key = (E)fromId.invoke(null, Integer.parseInt(element.getKey()));

                    fromId = valueClass.getDeclaredMethod("fromId", int.class);
                    F value = (F)fromId.invoke(null, element.getValue().getAsInt());

                    if (key != null && value != null) {
                        map.put(key, value);
                    }
                }
            }
        } catch (Exception ex) {
            SkyblockAddons.getInstance().getLogger().error("Failed to deserialize path: "+ path);
            ex.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum<?>, N extends Number> void deserializeEnumNumberMapFromID(Map<E, N> map, String path, Class<E> keyClass, Class<N> numberClass) {
        try {
            if (settingsConfig.has(path)) {
                for (Map.Entry<String, JsonElement> element : settingsConfig.getAsJsonObject(path).entrySet()) {
                    Method fromId = keyClass.getDeclaredMethod("fromId", int.class);
                    E key = (E)fromId.invoke(null, Integer.parseInt(element.getKey()));
                    if (key != null) {
                        map.put(key, (N)getNumber(element.getValue(), numberClass));
                    }
                }
            }
        } catch (Exception ex) {
            SkyblockAddons.getInstance().getLogger().error("Failed to deserialize path: "+ path);
            ex.printStackTrace();
        }
    }

    private <N extends Number> void deserializeNumber(Mutable<Number> number, String path, Class<N> numberClass) {
        try {
            if (settingsConfig.has(path)) {
                number.setValue(getNumber(settingsConfig.get(path), numberClass));
            }
        } catch (Exception ex) {
            SkyblockAddons.getInstance().getLogger().error("Failed to deserialize path: "+ path);
            ex.printStackTrace();
        }
    }

    private Number getNumber(JsonElement jsonElement, Class<? extends Number> numberClass) {
        if (numberClass == byte.class) { return jsonElement.getAsByte();
        } else if (numberClass == short.class) { return jsonElement.getAsShort();
        } else if (numberClass == int.class) { return jsonElement.getAsInt();
        } else if (numberClass == long.class) { return jsonElement.getAsLong();
        } else if (numberClass == float.class) { return jsonElement.getAsFloat();
        } else if (numberClass == double.class) { return jsonElement.getAsDouble(); }

        return null;
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum<?>> void deserializeEnumValueFromOrdinal(MutableObject<E> value, String path) {
        try {
            Class<? extends Enum<?>> enumClass = value.getValue().getDeclaringClass();
            Method method = enumClass.getDeclaredMethod("values");
            Object valuesObject = method.invoke(null);
            E[] values = (E[])valuesObject;

            if (settingsConfig.has(path)) {
                int ordinal = settingsConfig.get(path).getAsInt();
                if (values.length > ordinal) {
                    E enumValue = values[ordinal];
                    if (enumValue != null) {
                        value.setValue(values[ordinal]);
                    }
                }
            }
        } catch (Exception ex) {
            SkyblockAddons.getInstance().getLogger().error("Failed to deserialize path: "+ path);
            ex.printStackTrace();
        }
    }

    private void deserializeFeatureCoordsMapFromID(Map<Feature, CoordsPair> map, String path) {
        try {
            if (settingsConfig.has(path)) {
                for (Map.Entry<String, JsonElement> element : settingsConfig.getAsJsonObject(path).entrySet()) {
                    Feature feature = Feature.fromId(Integer.parseInt(element.getKey()));
                    if (feature != null) {
                        JsonArray coords = element.getValue().getAsJsonArray();
                        map.put(feature, new CoordsPair(coords.get(0).getAsInt(), coords.get(1).getAsInt()));
                    }
                }
            }
        } catch (Exception ex) {
            SkyblockAddons.getInstance().getLogger().error("Failed to deserialize path: "+ path);
            ex.printStackTrace();
        }
    }

    public void setAllCoordinatesToDefault() {
        setAnchorPointsToDefault();
        putDefaultBarSizes();
        guiScales.clear();
        for (Feature feature : Feature.getGuiFeatures()) {
            putDefaultCoordinates(feature);
        }
    }

    private void setAnchorPointsToDefault() {
        for (Feature feature : Feature.getGuiFeatures()) {
            EnumUtils.AnchorPoint anchorPoint = feature.getAnchorPoint();
            if (anchorPoint != null) {
                anchorPoints.put(feature, anchorPoint);
            }
        }
    }

    private void putDefaultCoordinates(Feature feature) {
        CoordsPair coords = feature.getDefaultCoordinates();
        if (coords != null) {
            coordinates.put(feature, coords);
        }
    }

    private void putDefaultBarSizes() {
        for (Feature feature : Feature.getGuiFeatures()) {
            CoordsPair size = feature.getDefaultBarSize();
            if (size != null) {
                barSizes.put(feature, size);
            }
        }
    }

    public static float normalizeValueNoStep(float value) {
        return MathHelper.clamp_float((snapNearDefaultValue(value) - ConfigValues.GUI_SCALE_MINIMUM) /
                (ConfigValues.GUI_SCALE_MAXIMUM - ConfigValues.GUI_SCALE_MINIMUM), 0.0F, 1.0F);
    }

    /** These two are taken from GuiOptionSlider. */
    public static float denormalizeScale(float value) {
        return snapNearDefaultValue(ConfigValues.GUI_SCALE_MINIMUM + (ConfigValues.GUI_SCALE_MAXIMUM - ConfigValues.GUI_SCALE_MINIMUM) *
                MathHelper.clamp_float(value, 0.0F, 1.0F));
    }
    public static float snapNearDefaultValue(float value) {
        if (value != 1 && value > 1-0.05 && value < 1+0.05) {
            return 1;
        }

        return value;
    }

    /**
     * @param feature The feature to check.
     * @return Whether the feature is remotely disabled.
     */
    public boolean isRemoteDisabled(Feature feature) {
        // Check all versions.
        if (main.getOnlineData().getDisabledFeatures().containsKey("all")) {
            return main.getOnlineData().getDisabledFeatures().get("all").contains(feature.getId());
        }

        // Check this version.
        String version = SkyblockAddons.VERSION;
        if (version.contains("-")) {
            version = version.split("-")[0];
        }
        if (main.getOnlineData().getDisabledFeatures().containsKey(version)) {
            return main.getOnlineData().getDisabledFeatures().get(version).contains(feature.getId());
        }

        return false;
    }

    /**
     * @param feature The feature to check.
     * @return Whether the feature is disabled.
     */
    public boolean isDisabled(Feature feature) {
        return disabledFeatures.contains(feature) || isRemoteDisabled(feature);
    }

    /**
     * @param feature The feature to check.
     * @return Whether the feature is enabled.
     */
    public boolean isEnabled(Feature feature) {
        return !isDisabled(feature);
    }

    public Color getColor(Feature feature, int alpha) {
        if (alpha == 255) {
            return getColor(feature);
        }

        if (chromaFeatures.contains(feature)) {
            Color color = ChromaManager.getCurrentColor();

            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }

        Color color = getColor(feature);

        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public Color getColor(Feature feature) {
        if (chromaFeatures.contains(feature)) {
            return ChromaManager.getCurrentColor();
        }

        ChatFormatting defaultColor = feature.getDefaultColor();
        return new Color(colors.getOrDefault(feature, defaultColor != null ? defaultColor.getRGB() : ChatFormatting.RED.getRGB()));
    }

    public ChatFormatting getRestrictedColor(Feature feature) {
        Integer featureColor = colors.get(feature);

        if (featureColor != null) {
            for (ChatFormatting chatFormatting : ChatFormatting.values()) {
                try {
                    if (chatFormatting.getRGB() == featureColor) {
                        return chatFormatting;
                    }
                }
                catch (IllegalArgumentException ignored) {
                    // This chat formatting has no color, let's ignore it.
                }
            }
        }

        return feature.getDefaultColor();
    }

    private boolean featureCoordinatesAreDefault(Feature feature) {
        if (feature.getDefaultCoordinates() == null) return true;
        if (!coordinates.containsKey(feature)) return true;

        return coordinates.get(feature).equals(feature.getDefaultCoordinates());
    }

    public void setColor(Feature feature, int color) {
        colors.put(feature, color);
    }

    public int getActualX(Feature feature) {
        int maxX = new ScaledResolution(Minecraft.getMinecraft()).getScaledWidth();
        return getAnchorPoint(feature).getX(maxX)+ getRelativeCoords(feature).getX();
    }

    public int getActualY(Feature feature) {
        int maxY = new ScaledResolution(Minecraft.getMinecraft()).getScaledHeight();
        return getAnchorPoint(feature).getY(maxY)+ getRelativeCoords(feature).getY();
    }

    public CoordsPair getSizes(Feature feature) {
        CoordsPair defaultSize = feature.getDefaultBarSize();
        return barSizes.getOrDefault(feature, defaultSize != null ? defaultSize : new CoordsPair(7,1));
    }

    public void setSizeX(Feature feature, int x) {
        CoordsPair coords = getSizes(feature);
        coords.setX(x);
        barSizes.put(feature, coords);
    }

    public void setSizeY(Feature feature, int y) {
        CoordsPair coords = getSizes(feature);
        coords.setY(y);
        barSizes.put(feature, coords);
    }

    public CoordsPair getRelativeCoords(Feature feature) {
        if (coordinates.containsKey(feature)) {
            return coordinates.get(feature);
        } else {
            putDefaultCoordinates(feature);
            if (coordinates.containsKey(feature)) {
                return coordinates.get(feature);
            } else {
                return new CoordsPair(0,0);
            }
        }
    }

    public void setCoords(Feature feature, int x, int y) {
        if (coordinates.containsKey(feature)) {
            coordinates.get(feature).setX(x);
            coordinates.get(feature).setY(y);
        } else {
            coordinates.put(feature, new CoordsPair(x, y));
        }
    }

    public void setClosestAnchorPoint(Feature feature) {
        int x1 = getActualX(feature);
        int y1 = getActualY(feature);
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        int maxX = sr.getScaledWidth();
        int maxY = sr.getScaledHeight();
        double shortestDistance = -1;
        EnumUtils.AnchorPoint closestAnchorPoint = EnumUtils.AnchorPoint.BOTTOM_MIDDLE; // default
        for (EnumUtils.AnchorPoint point : EnumUtils.AnchorPoint.values()) {
            double distance = Point2D.distance(x1, y1, point.getX(maxX), point.getY(maxY));
            if (shortestDistance == -1 || distance < shortestDistance) {
                closestAnchorPoint = point;
                shortestDistance = distance;
            }
        }
        int targetX = getActualX(feature);
        int targetY = getActualY(feature);
        int x = targetX-closestAnchorPoint.getX(sr.getScaledWidth());
        int y = targetY-closestAnchorPoint.getY(sr.getScaledHeight());
        anchorPoints.put(feature, closestAnchorPoint);
        setCoords(feature, x, y);
    }

    public EnumUtils.AnchorPoint getAnchorPoint(Feature feature) {
        EnumUtils.AnchorPoint defaultPoint = feature.getAnchorPoint();

        return anchorPoints.getOrDefault(feature, defaultPoint != null ? defaultPoint : EnumUtils.AnchorPoint.BOTTOM_MIDDLE);
    }

    public Set<Integer> getLockedSlots() {
        String profile = main.getUtils().getProfileName();
        if (!profileLockedSlots.containsKey(profile)) {
            profileLockedSlots.put(profile, new HashSet<>());
        }

        return profileLockedSlots.get(profile);
    }

    public void setGuiScale(Feature feature, float scale) {
        guiScales.put(feature, scale);
    }

    public float getGuiScale(Feature feature) {
        return getGuiScale(feature, true);
    }

    public float getGuiScale(Feature feature, boolean denormalized) {
        float value = ConfigValues.DEFAULT_GUI_SCALE;
        if (guiScales.containsKey(feature)) {
            value = guiScales.get(feature);
        }
        if (denormalized) {
            value = denormalizeScale(value);
        }
        return value;
    }

    public int getWarningSeconds() {
        return warningSeconds.getValue();
    }

    public void setWarningSeconds(int warningSeconds) {
        this.warningSeconds.setValue(warningSeconds);
    }

    public Language getLanguage() {
        return language.getValue();
    }

    public void setLanguage(Language language) {
        this.language.setValue(language);
    }

    public EnumUtils.BackpackStyle getBackpackStyle() {
        return backpackStyle.getValue();
    }

    public void setBackpackStyle(EnumUtils.BackpackStyle backpackStyle) {
        this.backpackStyle.setValue(backpackStyle);
    }

    public EnumUtils.PowerOrbDisplayStyle getPowerOrbDisplayStyle() {
        return powerOrbDisplayStyle.getValue();
    }

    public void setPowerOrbDisplayStyle(EnumUtils.PowerOrbDisplayStyle powerOrbDisplayStyle) {
        this.powerOrbDisplayStyle.setValue(powerOrbDisplayStyle);
    }

    public EnumUtils.TextStyle getTextStyle() {
        return textStyle.getValue();
    }

    public void setTextStyle(EnumUtils.TextStyle textStyle) {
        this.textStyle.setValue(textStyle);
    }

    public float getChromaSpeed() {
        return chromaSpeed.getValue();
    }

    public void setChromaSpeed(float chromaSpeed) {
        this.chromaSpeed.setValue(chromaSpeed);
    }

    public EnumUtils.ChromaMode getChromaMode() {
        return chromaMode.getValue();
    }

    public void setChromaMode(EnumUtils.ChromaMode chromaMode) {
        this.chromaMode.setValue(chromaMode);
    }

    public void setChromaFadeWidth(float chromaFadeWidth) {
        this.chromaFadeWidth.setValue(chromaFadeWidth);
    }

    public float getChromaFadeWidth() {
        return chromaFadeWidth.getValue();
    }

    public void setDiscordDetails(DiscordStatus discordDetails) {
        this.discordDetails.setValue(discordDetails);
    }

    public void setDiscordStatus(DiscordStatus discordStatus) {
        this.discordStatus.setValue(discordStatus);
    }

    public DiscordStatus getDiscordStatus() {
        return discordStatus != null ? discordStatus.getValue() : DiscordStatus.NONE;
    }

    public DiscordStatus getDiscordDetails() {
        return discordDetails != null ? discordDetails.getValue() : DiscordStatus.NONE;
    }

    public DiscordStatus getDiscordAutoDefault() {
        return discordAutoDefault != null ? discordAutoDefault.getValue() : DiscordStatus.NONE;
    }

    public void setDiscordAutoDefault(DiscordStatus discordAutoDefault) {
        this.discordAutoDefault.setValue(discordAutoDefault);
    }
}
