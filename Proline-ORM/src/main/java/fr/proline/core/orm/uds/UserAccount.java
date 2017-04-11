package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

import fr.proline.core.orm.util.JsonSerializer;

/**
 * The persistent class for the user_account database table.
 * 
 */

@Entity
@Table(name = "user_account")
public class UserAccount implements Serializable {
    
	public enum UserGroupType {
		USER, ADMIN
    };
    
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "creation_mode")
    private String creationMode;

    private String login;
    
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // Transient Variables not saved in database
    @Transient
    private Map<String, Object> serializedPropertiesMap;
    
    public UserAccount() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public String getCreationMode() {
	return this.creationMode;
    }

    public void setCreationMode(String creationMode) {
	this.creationMode = creationMode;
    }

    public String getLogin() {
	return this.login;
    }

    public void setLogin(String login) {
	this.login = login;
    }

    public String getPasswordHash() {
	return this.passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
	this.passwordHash = passwordHash;
    }
    
    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    
    @SuppressWarnings("unchecked")
	public Map<String, Object> getSerializedPropertiesAsMap() throws Exception {
	if ((serializedPropertiesMap == null) && (serializedProperties != null)) {
	    serializedPropertiesMap = JsonSerializer.getMapper().readValue(getSerializedProperties(),Map.class);
	}
	return serializedPropertiesMap;
    }

    public void setSerializedPropertiesAsMap(Map<String, Object> serializedPropertiesMap) throws Exception {
	this.serializedPropertiesMap = serializedPropertiesMap;
	this.serializedProperties = JsonSerializer.getMapper().writeValueAsString(serializedPropertiesMap);
    }
}
