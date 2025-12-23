package dev.coolrequest.gosync.ssh;

public class HostInfo {
    private String host;
    private int port;
    private String username;
    private String password;
    private int hostType;
    private String hostExtJSON;
    private int sort;

    public enum HostType {
        SSH(1),
        JUMPSERVER(2);

        private final int value;

        HostType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static HostType fromValue(int value) {
            for (HostType type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            return SSH;
        }
    }

    public int getSort() {
        return sort;
    }

    public void setSort(int sort) {
        this.sort = sort;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getHostType() {
        return hostType;
    }

    public void setHostType(int hostType) {
        this.hostType = hostType;
    }

    public String getHostExtJSON() {
        return hostExtJSON;
    }

    public void setHostExtJSON(String hostExtJSON) {
        this.hostExtJSON = hostExtJSON;
    }


    public String getIpAddress() {
        return host + ":" + port;
    }

    public HostType getType() {
        return HostType.fromValue(hostType);
    }

    public void setType(HostType type) {
        this.hostType = type.getValue();
    }
}
