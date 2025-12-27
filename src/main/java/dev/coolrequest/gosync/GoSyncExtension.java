package dev.coolrequest.gosync;

import org.gradle.api.provider.Property;

public interface GoSyncExtension {
    Property<String> getServerType();

    Property<String> getServerAddress();

    Property<String> getUserName();

    Property<String> getUserPass();

    Property<String> getAfterConnectedCommand();

    Property<String> getBeforeCommand();

    Property<String> getAfterCommand();

    Property<String> getLibDirectory();

    Property<String> getMainJarDirectory();

    Property<Integer> getPort();

}
