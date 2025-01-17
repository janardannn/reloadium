package rw.action;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import rw.audit.RwSentry;
import rw.consts.Const;
import rw.handler.runConf.BaseRunConfHandler;
import rw.handler.runConf.RunConfHandlerFactory;
import rw.service.Service;

import java.util.TimerTask;


public abstract class WithReloaderBase extends AnAction {
    RunType runType;

    private static final Logger LOGGER = Logger.getInstance(WithReloaderBase.class);

    public void update(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setVisible(true);

        Project project = getEventProject(e);

        if (project == null)
            return;

        RunnerAndConfigurationSettings conf = this.getConfiguration(e);

        if (!this.canRun(e)) {
            presentation.setEnabled(false);
        } else {
            this.handleRunningConfs(project, e, conf);
            this.setEnabledText(e, conf);
            presentation.setEnabled(true);
        }
    }

    public boolean canRun(@NotNull AnActionEvent e) {
        Service service = Service.get();
        RunnerAndConfigurationSettings conf = this.getConfiguration(e);

        if (conf == null) {
            return false;
        }

        BaseRunConfHandler handler = RunConfHandlerFactory.factory(conf.getConfiguration());

        if (handler == null) {
            return false;
        }

        if (!handler.canRun()) {
            return false;
        }

        return service.canRun(conf);
    }

    public void setEnabledText(@NotNull AnActionEvent e, RunnerAndConfigurationSettings conf) {
        String text = String.format("%s '%s' with %s", this.getExecutor().getActionName(),
                conf.getName(),
                StringUtil.capitalize(Const.get().packageName));
        e.getPresentation().setText(text);
    }

    public void actionPerformed(@NotNull AnActionEvent e) {
        LOGGER.info("Performing action");
        try {
            Project project = getEventProject(e);

            if (project == null)
                return;

            RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
            RunnerAndConfigurationSettings conf = this.getConfiguration(e);

            ExecutionEnvironment environment = this.getEnvironment(conf);

            RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();

            if (!runManager.hasSettings(settings)) {
                runManager.addConfiguration(settings);
            }
            runManager.setSelectedConfiguration(settings);

            if (ExecutionManager.getInstance(project).isStarting(environment))
                return;

            ExecutionManager.getInstance(project).restartRunProfile(environment);
        } catch (Exception exception) {
            RwSentry.get().captureException(exception);
        }
    }

    protected ExecutionEnvironment getEnvironment(RunnerAndConfigurationSettings conf) {
        Executor executor = this.getExecutor();
        ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(executor, conf);

        BaseRunConfHandler handler = RunConfHandlerFactory.factory(conf.getConfiguration());

        if (handler.isActivated()) {
            return builder.build();
        }

        handler.beforeRun(this.runType);

        TimerTask task = new TimerTask() {
            public void run() {
                if (handler.isActivated())
                    handler.afterRun();
            }
        };

        new java.util.Timer().schedule(task, 5000);

        ExecutionEnvironment ret = builder.build(new ProgramRunner.Callback() {
            @Override
            public void processStarted(RunContentDescriptor descriptor) {
                handler.onProcessStarted(descriptor);
                handler.afterRun();
                descriptor.getProcessHandler().addProcessListener(new ProcessAdapter() {
                    @Override
                    public void processTerminated(@NotNull ProcessEvent event) {
                        handler.onProcessExit();
                    }
                });
            }
        });

        return ret;
    }

    protected RunnerAndConfigurationSettings getConfiguration(@NotNull AnActionEvent e) {
        Project project = getEventProject(e);
        RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
        RunnerAndConfigurationSettings conf = runManager.getSelectedConfiguration();
        return conf;
    }


    private void handleRunningConfs(Project project, @NotNull AnActionEvent e, RunnerAndConfigurationSettings conf) {
        ExecutionManagerImpl executionManager = ExecutionManagerImpl.getInstance(project);

        Condition condition = object -> true;

        boolean isRunning = false;

        for (Object p : executionManager.getRunningDescriptors(condition)) {
            RunContentDescriptor descr = (RunContentDescriptor) p;
            if (conf.getName().equals(descr.getDisplayName()) && executionManager.getExecutors(descr).contains(this.getExecutor())) {
                isRunning = true;
            }
        }

        if (isRunning) {
            this.setRunningIcon(e);
        } else {
            this.setNotRunningIcon(e);
        }
    }

    abstract Executor getExecutor();

    abstract void setRunningIcon(AnActionEvent e);

    abstract void setNotRunningIcon(AnActionEvent e);
}