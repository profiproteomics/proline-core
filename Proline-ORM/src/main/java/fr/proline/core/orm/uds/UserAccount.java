package fr.proline.core.orm.uds;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * The persistent class for the user_account database table.
 * 
 */

@Entity
@Table(name = "user_account")
public class UserAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "creation_mode")
    private String creationMode;

    private String login;
    
    @Column(name = "password_hash")
    private String hashPassword;

    @Column(name = "serialized_properties")
    private String serializedProperties;

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

    public String getPassword() {
	return this.hashPassword;
    }

    public void setPassword(String password) {
	this.hashPassword = password;
    }
    
    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

}
