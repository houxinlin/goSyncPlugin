package dev.coolrequest.gosync;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class GoSyncTask extends DefaultTask {

    @Input
    @Optional
    public abstract Property<String> getServerType();

    @Input
    @Optional
    public abstract Property<String> getServerAddress();

    @Input
    @Optional
    public abstract Property<String> getUserName();

    @Input
    @Optional
    public abstract Property<String> getUserPass();

    @Input
    @Optional
    public abstract Property<String> getAfterConnectedCommand();

    @Input
    @Optional
    public abstract Property<String> getLibDirectory();

    @Input
    @Optional
    public abstract Property<String> getMainJarDirectory();

    @Input
    @Optional
    public abstract Property<Integer> getPort();

    @Internal
    public String getUserNameValue() {
        if (getUserName().isPresent()) {
            String value = getUserName().get();
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return System.getenv("goSync.userName");
    }

    @Internal
    public String getUserPassValue() {
        if (getUserPass().isPresent()) {
            String value = getUserPass().get();
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return System.getenv("goSync.userPass");
    }

    @TaskAction
    public void sync() {
        if (!getServerType().isPresent() || getServerType().get().isEmpty()) {
            throw new IllegalArgumentException("serverType 不能为空");
        }
        if (!getServerAddress().isPresent() || getServerAddress().get().isEmpty()) {
            throw new IllegalArgumentException("serverAddress 不能为空");
        }
        String userName = getUserNameValue();
        if (userName == null || userName.isEmpty()) {
            throw new IllegalArgumentException("userName 不能为空");
        }
        String userPass = getUserPassValue();
        if (userPass == null || userPass.isEmpty()) {
            throw new IllegalArgumentException("userPass 不能为空");
        }
        if (!getLibDirectory().isPresent() || getLibDirectory().get().isEmpty()) {
            throw new IllegalArgumentException("libDirectory 不能为空");
        }
        if (!getMainJarDirectory().isPresent() || getMainJarDirectory().get().isEmpty()) {
            throw new IllegalArgumentException("mainJarDirectory 不能为空");
        }

        org.gradle.api.tasks.bundling.Jar jarTask = (org.gradle.api.tasks.bundling.Jar) getProject().getTasks().findByName("jar");
        File jarFile = null;
        if (jarTask != null) {
            if (jarTask.getEnabled() && jarTask.getArchiveFile().isPresent()) {
                jarFile = jarTask.getArchiveFile().get().getAsFile();
            }
        }
        List<String> libs = new ArrayList<>();
        Configuration runtimeClasspath = getProject().getConfigurations().getByName("runtimeClasspath");
        runtimeClasspath.getIncoming().getArtifacts().getArtifacts().forEach(artifact -> {
            libs.add(artifact.getFile().getAbsolutePath());
        });
        if (jarFile == null) {
            return;
        }
        Sync sync = new Sync(libs, jarFile.getAbsolutePath(), this);
        sync.start();
    }
}

