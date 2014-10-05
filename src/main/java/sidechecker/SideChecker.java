package sidechecker;

import com.google.common.eventbus.EventBus;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

@IFMLLoadingPlugin.TransformerExclusions(value = {"sidechecker.", "sidechecker.SideChecker", "sidechecker.SideCheckerTransformer"})
@IFMLLoadingPlugin.SortingIndex(Integer.MAX_VALUE)
public class SideChecker extends DummyModContainer implements IFMLLoadingPlugin {
    public final SideChecker instance;

    protected final ModMetadata md = new ModMetadata();

    public SideChecker() {
        instance = this;
        FMLRelaunchLog.info("[SideChecker] SideChecker Init");
        md.autogenerated = false;
        md.authorList.add("RWTema");
        md.credits = "RWTema";
        md.modId = getModId();
        md.version = getVersion();
        md.name = getName();
        md.description = "SideOnly Checker Transformer";
    }

    @Mod.EventHandler
    public void load(FMLInitializationEvent event) {
    }

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        return true;
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{"sidechecker.SideCheckerTransformer"};
    }

    @Override
    public String getModContainerClass() {
        return "sidechecker.SideChecker";
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {

    }

    @Override
    public String getModId() {
        return "SideChecker";
    }

    @Override
    public String getName() {
        return "SideOnly Checker";
    }

    @Override
    public String getVersion() {
        return "1";
    }

    @Override
    public String getDisplayVersion() {
        return getVersion();
    }

    @Override
    public ModMetadata getMetadata() {
        return md;
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}