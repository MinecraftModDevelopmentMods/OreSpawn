package zone.moddev.mc.orespawn.integration;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.Forge25TestBootstrap;
import zone.moddev.mc.orespawn.util.JsonCopies;

class FormationOverrideTest {
    @SuppressWarnings("unchecked")
    @Test void onlyUntouchedPackagedDefaultsAreTuned() throws Exception {
        Forge25TestBootstrap.registerVanilla();
        Field resources=WorldgenIntegrationManager.class.getDeclaredField("RESOURCE_PROVIDERS");resources.setAccessible(true);
        Field files=WorldgenIntegrationManager.class.getDeclaredField("FILE_PROVIDERS");files.setAccessible(true);
        Map<String,JsonObject> packaged=(Map<String,JsonObject>)resources.get(null), external=(Map<String,JsonObject>)files.get(null);
        JsonObject stock=new JsonObject();stock.addProperty("chance",.12);stock.addProperty("enabled",true);
        JsonObject definitions=new JsonObject();definitions.add("test:body",stock);
        JsonObject provider=new JsonObject();provider.add("rock_formations",definitions);
        JsonObject tuning=JsonCopies.copy(definitions);tuning.getAsJsonObject("test:body").addProperty("chance",.45);
        JsonObject beforeResource=packaged.get("test"), beforeFile=external.remove("test");
        packaged.put("test",provider);
        try {
            JsonObject target=JsonCopies.copy(provider);
            target.getAsJsonObject("rock_formations").getAsJsonObject("test:body").addProperty("source_provider","test");
            WorldgenIntegrationManager.applyStockFormationTuning(target,tuning);
            assertEquals(.45,target.getAsJsonObject("rock_formations").getAsJsonObject("test:body").get("chance").getAsDouble());
            for(String edit:Arrays.asList("chance","enabled")) {
                target=JsonCopies.copy(provider);
                JsonObject rule=target.getAsJsonObject("rock_formations").getAsJsonObject("test:body");
                if(edit.equals("chance"))rule.addProperty(edit,.2);else rule.addProperty(edit,false);
                JsonObject authored=JsonCopies.copy(target);WorldgenIntegrationManager.applyStockFormationTuning(target,tuning);
                assertEquals(authored,target);
            }
            target=new JsonObject();WorldgenIntegrationManager.applyStockFormationTuning(target,tuning);assertFalse(target.has("rock_formations"));
            external.put("test",provider);target=JsonCopies.copy(provider);
            WorldgenIntegrationManager.applyStockFormationTuning(target,tuning);assertEquals(provider,target);
        } finally {
            if(beforeResource==null)packaged.remove("test");else packaged.put("test",beforeResource);
            if(beforeFile==null)external.remove("test");else external.put("test",beforeFile);
        }
    }
}
