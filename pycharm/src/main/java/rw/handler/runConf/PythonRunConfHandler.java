package rw.handler.runConf;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import org.jetbrains.annotations.Nullable;
import rw.action.RunType;
import rw.audit.RwSentry;
import rw.consts.Const;
import rw.session.Session;
import rw.settings.PluginState;
import rw.settings.Settings;
import rw.util.EnvUtils;

import java.util.ArrayList;
import java.util.List;


public class PythonRunConfHandler extends BaseRunConfHandler {
    AbstractPythonRunConfiguration<?> runConf;
    AbstractPythonRunConfiguration<?> origRunConf;
    Session session;

    public PythonRunConfHandler(RunConfiguration runConf) {
        super(runConf);
        this.runConf = (AbstractPythonRunConfiguration<?>) runConf;
        this.origRunConf = (AbstractPythonRunConfiguration<?>) runConf.clone();
        this.session = new Session(this.runConf.getProject(), this);
    }

    @Override
    public void beforeRun(RunType runType) {
        String command;

        if (runType == RunType.DEBUG) {
            command = "pydev_proxy";
        } else {
            command = "run";
        }

        String pathSep = System.getProperty("path.separator");

        this.runConf.setInterpreterOptions(String.format("-m %s %s", Const.get().packageName, command));

        this.session.start();

        // Set Envs
        this.runConf.getEnvs().put(Const.get().ideNameEnvVar, ApplicationInfo.getInstance().getFullApplicationName());
        this.runConf.getEnvs().put(Const.get().idePluginVersionEnvVar, Const.get().version);
        this.runConf.getEnvs().put(Const.get().ideVersionEnvVar, ApplicationInfo.getInstance().getFullVersion());

        PluginState state = Settings.getInstance(this.runConf.getProject()).getState();

        this.runConf.getEnvs().put("RW_DEBUGGERSPEEDUPS", EnvUtils.boolToEnv(state.debuggerSpeedups));
        this.runConf.getEnvs().put("RW_VERBOSE", EnvUtils.boolToEnv(state.verbose));
        this.runConf.getEnvs().put("RW_CACHE", EnvUtils.boolToEnv(state.cacheEnabled));
        this.runConf.getEnvs().put("RW_PRINTLOGO", EnvUtils.boolToEnv(state.printLogo));
        this.runConf.getEnvs().put("RW_WATCHCWD", EnvUtils.boolToEnv(state.watchCwd));
        this.runConf.getEnvs().put("RW_IDE_SERVERPORT", String.valueOf(this.session.getPort()));
        this.runConf.getEnvs().put("PYDEVD_USE_CYTHON", "NO");

        List<String> reloadiumPath = new ArrayList<String>(state.reloadiumPath);

        if (state.watchSourceRoots){
            @Nullable Module module = this.runConf.getModule();
            if (module != null) {
                VirtualFile[] sourceRootsRaw = ModuleRootManager.getInstance(module).getSourceRoots(false);

                for (VirtualFile f : sourceRootsRaw) {
                    if (TempFileSystem.class.isAssignableFrom(f.getFileSystem().getClass())) {
                        continue;
                    }
                    try {
                        reloadiumPath.add(this.convertPathToRemote(f.toNioPath().toString()));
                    }
                    catch (Exception e) {
                        RwSentry.get().captureException(e);
                    }
                }
            }
        }

        this.runConf.getEnvs().put("RELOADIUMPATH", String.join(pathSep, reloadiumPath));

        // Set PYTHONPATH
        String pythonpath = this.runConf.getEnvs().getOrDefault("PYTHONPATH", "");

        assert this.sdkHandler != null;
        String packagePath = this.sdkHandler.getPackageDir().toString();

        if (!pythonpath.isBlank()) {
            pythonpath = String.format("%s%s%s", packagePath, pathSep, pythonpath);
        } else
            pythonpath = packagePath;

        this.runConf.getEnvs().put("PYTHONPATH", pythonpath);
    }

    public void onProcessStarted(RunContentDescriptor descriptor) {

    }

    @Override
    public void onProcessExit() {
        super.onProcessExit();
        this.session.close();
    }

    @Override
    public void afterRun() {
        this.runConf.setInterpreterOptions(this.origRunConf.getInterpreterOptions());
        runConf.setEnvs(this.origRunConf.getEnvs());
    }

    public boolean isActivated() {
        boolean ret = this.runConf.getInterpreterOptions().contains("reloadium");
        return ret;
    }

    @Override
    public boolean canRun() {
        if (this.sdkHandler == null) {
            return false;
        }

        return this.sdkHandler.isValid();
    }
}
