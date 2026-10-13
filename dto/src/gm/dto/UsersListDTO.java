package gm.dto;

import java.util.ArrayList;
import java.util.List;

/** The users list together with the server state version it was taken from. */
public class UsersListDTO {

    private final long version;
    private final List<UserDTO> users;

    public UsersListDTO(long version, List<UserDTO> users) {
        this.version = version;
        this.users = new ArrayList<>(users);
    }

    public long getVersion() {
        return version;
    }

    public List<UserDTO> getUsers() {
        return users;
    }
}
