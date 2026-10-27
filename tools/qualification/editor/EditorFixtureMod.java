package zone.moddev.mc.orespawn.client;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;
import zone.moddev.mc.orespawn.client.GeologyEditorSession.OreSourceGroup;

/** Disposable editor fixtures; this mod is never part of an OreSpawn release. */
@Mod(modid = "editorfixtures", name = "Ore Sources visual test", version = "1",
        acceptedMinecraftVersions = "[1.12.2]", dependencies = "required-after:orespawn")
public final class EditorFixtureMod {
    private static final List<String> FIXTURES = Arrays.asList(
            "os1-import", "os3-import", "custom-channel", "fresh-standard");
    private static final String GROUP = "orespawn:iron|minecraft:overworld";
    private final String requestedFixture = System.getProperty("editorfixtures.fixture", "");
    private final FixtureMenu menu = new FixtureMenu();
    private OreSpawnWorldSettingsScreen root;
    private GeologyEditorSession session;
    private WorldGeologyProfile starting;
    private String selected;
    private int fixtureIndex;
    private int stage;
    private int ticks;
    private boolean opened;
    private boolean editing;
    private boolean finished;
    private String pendingScreenshot;
    private int captureFrames;
    private String result = "Choose a profile. All changes stay in this disposable test folder.";

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void capture(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (pendingScreenshot == null || !(event.getGui() instanceof OreSourceGroupSettingsScreen)) return;
        if (++captureFrames < 5) return;
        Minecraft mc = Minecraft.getMinecraft();

        // Capture after the screen has drawn, not between client ticks.
        int texture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        try {
            ScreenShotHelper.saveScreenshot(mc.gameDir, pendingScreenshot,
                    mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        } finally {
            GlStateManager.bindTexture(texture);
        }
        pendingScreenshot = null;
        captureFrames = 0;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        Minecraft mc = Minecraft.getMinecraft();

        try {
            if (!opened && mc.currentScreen instanceof GuiMainMenu) {
                opened = true;
                mc.displayGuiScreen(menu);
            }
            if (!opened || !Boolean.getBoolean("editorfixtures.automated")) return;
            require(ticks < 3600, "The editor test timed out");
            if (++ticks % 12 != 0) return;

            switch (stage) {
                case 0:
                    if (!requestedFixture.isEmpty()) {
                        fixtureIndex = FIXTURES.indexOf(requestedFixture);
                        require(fixtureIndex >= 0, "Unknown editor fixture " + requestedFixture);
                    }
                    open(FIXTURES.get(fixtureIndex));
                    showPlacementScreen();
                    pendingScreenshot = selected + "-before.png";
                    stage = 1;
                    break;
                case 1:
                    if (pendingScreenshot != null) return;
                    inspectAndEdit(mc);
                    mc.displayGuiScreen(root);
                    press(root, "gui.cancel");
                    require(starting.toJson().equals(WorldGeologyProfileManager.pendingNewWorldProfile().toJson()),
                            "Cancel changed the accepted profile");
                    stage = 2;
                    break;
                case 2:
                    open(FIXTURES.get(fixtureIndex));
                    showPlacementScreen();
                    pendingScreenshot = selected + "-save.png";
                    stage = 3;
                    break;
                case 3:
                    if (pendingScreenshot != null) return;
                    inspectAndEdit(mc);
                    mc.displayGuiScreen(root);
                    press(root, "gui.done");
                    require(mc.currentScreen == menu, "Done did not return to the fixture menu");
                    WorldGeologyProfile accepted = WorldGeologyProfileManager.pendingNewWorldProfile();
                    GeologyEditorSession reloaded = new GeologyEditorSession(accepted);
                    String channel = selected.equals("custom-channel") ? "example:custom_iron" : "orespawn:standard";
                    require("example:alternative_iron".equals(ironGroup(reloaded).placements.get(channel)),
                            "Done did not retain the chosen source");
                    require(starting.toJson().get("ores").equals(accepted.toJson().get("ores")),
                            "Editing a placement source changed customised ore rules");
                    write(selected + "-accepted.json", accepted.toJson());
                    JsonObject disk = new JsonParser().parse(new String(Files.readAllBytes(
                            output().resolve(selected + "-accepted.json")), StandardCharsets.UTF_8)).getAsJsonObject();
                    require(accepted.toJson().equals(WorldGeologyProfile.fromJson(disk, accepted).toJson()),
                            "The accepted profile changed on reload");
                    checkManagementSwitch(accepted, channel);
                    fixtureIndex++;
                    if (!requestedFixture.isEmpty() || fixtureIndex == FIXTURES.size()) {
                        Files.write(output().resolve("PASS.txt"),
                                "Editor checks passed: screen, selection, Cancel, Done and file reload.\n"
                                        .getBytes(StandardCharsets.UTF_8));
                        finished = true;
                        mc.shutdown();
                    } else {
                        stage = 0;
                    }
                    break;
                default:
                    throw new IllegalStateException("Unexpected fixture stage " + stage);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try {
                Files.write(output().resolve("FAIL.txt"), failure.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException writing) {
                failure.addSuppressed(writing);
            }
            finished = true;
            mc.shutdown();
        }
    }

    private void open(String name) throws Exception {
        selected = name;
        Path saved = output().resolve(name + "-manual.json");
        JsonObject json;
        if (!Boolean.getBoolean("editorfixtures.automated") && Files.exists(saved)) {
            json = new JsonParser().parse(new String(Files.readAllBytes(saved), StandardCharsets.UTF_8)).getAsJsonObject();
        } else {
            try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream(
                    "/ore-source-editor/" + name + ".json"), StandardCharsets.UTF_8)) {
                json = new JsonParser().parse(reader).getAsJsonObject();
            }
        }

        starting = WorldGeologyProfile.fromJson(json, WorldGeologyProfile.recommended(false));
        write(name + "-input.json", starting.toJson());
        WorldGeologyProfileManager.setPendingNewWorldProfile(starting);
        root = new OreSpawnWorldSettingsScreen(menu, starting);
        session = (GeologyEditorSession) field(root, "session");
        editing = true;
        Minecraft.getMinecraft().displayGuiScreen(root);
    }

    private void showPlacementScreen() {
        OreSourceListScreen sources = new OreSourceListScreen(root, session);
        Minecraft.getMinecraft().displayGuiScreen(sources);
        Minecraft.getMinecraft().displayGuiScreen(new OreSourceGroupSettingsScreen(sources, session, GROUP));
    }

    private void inspectAndEdit(Minecraft mc) throws Exception {
        require(mc.currentScreen instanceof OreSourceGroupSettingsScreen, "Placement screen did not open");
        OreSourceGroupSettingsScreen screen = (OreSourceGroupSettingsScreen) mc.currentScreen;
        CompactScrollList rows = (CompactScrollList) field(screen, "placementList");
        require(rows.size() == 1, "An old inferred channel left a duplicate row");
        require(!rows.rowText(0).contains(I18n.format("label.orespawn.ore_source.missing")),
                "A loaded placement source was labelled missing");
        require(rows.rowEnabled(0), "The two-source fixture should allow a selection");
        rows.onRowPressed(0);
    }

    private static void press(OreSpawnScreen screen, String translation) {
        for (GuiButton button : screen.buttons) {
            if (button instanceof Button && I18n.format(translation).equals(
                    TextFormatting.getTextWithoutFormattingCodes(button.displayString))) {
                ((Button) button).press();
                return;
            }
        }
        throw new IllegalStateException("Could not find " + translation);
    }

    private void checkManagementSwitch(WorldGeologyProfile profile, String channel) throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        OreSpawnWorldSettingsScreen settings = new OreSpawnWorldSettingsScreen(menu, profile);
        GeologyEditorSession working = (GeologyEditorSession) field(settings, "session");
        String choices = working.root().getAsJsonObject("ore_source_policies")
                .getAsJsonObject(GROUP).get("placement_sources").toString();

        for (int i = 0; i < 2; i++) {
            mc.displayGuiScreen(settings);
            ((Button) field(settings, "vanillaOresButton")).press();
            press(settings, "button.orespawn.materials");
            require(choices.equals(working.root().getAsJsonObject("ore_source_policies")
                    .getAsJsonObject(GROUP).get("placement_sources").toString()),
                    "Manage Vanilla Ores changed a saved choice");
            require("example:alternative_iron".equals(ironGroup(working).placements.get(channel)),
                    "Manage Vanilla Ores lost the displayed choice");
        }
        mc.displayGuiScreen(menu);
    }

    private static OreSourceGroup ironGroup(GeologyEditorSession session) {
        for (OreSourceGroup group : session.oreSourceGroups()) {
            if (GROUP.equals(group.key)) return group;
        }
        throw new IllegalStateException("The Iron group disappeared");
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static Path output() throws IOException {
        Path directory = Minecraft.getMinecraft().gameDir.toPath().resolve("editor-results");
        Files.createDirectories(directory);
        return directory;
    }

    private static void write(String name, JsonObject json) throws IOException {
        String text = new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n";
        Files.write(output().resolve(name), text.getBytes(StandardCharsets.UTF_8));
    }

    private final class FixtureMenu extends GuiScreen {
        @Override
        public void initGui() {
            if (editing) {
                editing = false;
                WorldGeologyProfile accepted = WorldGeologyProfileManager.pendingNewWorldProfile();
                boolean changed = !starting.toJson().equals(accepted.toJson());
                result = changed ? "Saved test changes for " + selected + "." : "No changes saved for " + selected + ".";
                if (changed) {
                    try {
                        write(selected + "-manual.json", accepted.toJson());
                    } catch (IOException failure) {
                        throw new IllegalStateException("Could not save the disposable profile", failure);
                    }
                }
            }

            String[] labels = { "OS1 imported profile", "OS3 imported profile", "Explicit custom channel", "Fresh standard channel" };
            for (int i = 0; i < FIXTURES.size(); i++) {
                buttonList.add(new GuiButton(i, width / 2 - 155, 60 + i * 26, 310, 20, labels[i]));
            }
            buttonList.add(new GuiButton(10, width / 2 - 155, height - 28, 310, 20, "Quit test"));
        }

        @Override
        protected void actionPerformed(GuiButton button) throws IOException {
            if (button.id == 10) {
                mc.shutdown();
                return;
            }

            try {
                open(FIXTURES.get(button.id));
            } catch (Exception failure) {
                throw new IOException("Could not open the fixture", failure);
            }
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(fontRenderer, "Ore Sources visual test", width / 2, 18, 0xFFFFFF);
            List<String> lines = fontRenderer.listFormattedStringToWidth(result, width - 40);
            int y = 174;
            for (String line : lines) {
                drawCenteredString(fontRenderer, line, width / 2, y, 0xCCCCCC);
                y += 11;
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        protected void keyTyped(char character, int keyCode) throws IOException {
            if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE) mc.shutdown();
            else super.keyTyped(character, keyCode);
        }
    }
}
