package dev.coolrequest.gosync;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class GoSyncPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        GoSyncExtension extension = project.getExtensions().create("goSync", GoSyncExtension.class);

        project.getTasks().register("goSync", GoSyncTask.class, task -> {
            task.setGroup("deployment");
            task.setDescription("同步构建产物到远程服务器");

            task.getServerType().convention(extension.getServerType());
            task.getServerAddress().convention(extension.getServerAddress());
            task.getUserName().convention(extension.getUserName());
            task.getUserPass().convention(extension.getUserPass());
            task.getAfterConnectedCommand().convention(extension.getAfterConnectedCommand());
            task.getBeforeCommand().convention(extension.getBeforeCommand());
            task.getAfterCommand().convention(extension.getAfterCommand());
            task.getLibDirectory().convention(extension.getLibDirectory());
            task.getMainJarDirectory().convention(extension.getMainJarDirectory());
            task.getPort().convention(extension.getPort());
        });

        project.afterEvaluate(p -> {
            GoSyncTask goSyncTask = (GoSyncTask) p.getTasks().findByName("goSync");
            if (goSyncTask != null) {
                org.gradle.api.Task jarTask = p.getTasks().findByName("jar");

                if (jarTask != null) {
                    goSyncTask.dependsOn(jarTask);
                }
            }
        });
    }
}
