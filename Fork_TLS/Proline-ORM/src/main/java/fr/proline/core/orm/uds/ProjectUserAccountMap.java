package fr.proline.core.orm.uds;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 * The persistent class for the project_user_account_map database table.
 * 
 */
@Entity
@Table(name="project_user_account_map")
@NamedQueries({
	@NamedQuery(name = "findProjectUserMapsByMembership", query = "Select pToUser from ProjectUserAccountMap pToUser where userAccount.id=:id")
})

public class ProjectUserAccountMap  implements Serializable {
	  private static final long serialVersionUID = 1L;
	  
		@EmbeddedId
		private ProjectUserAccountMapPK id;
		
		// bi-directional many-to-one association to Project
		@ManyToOne
		@JoinColumn(name = "project_id")
		@MapsId("projectId")
		private Project project;
		
		// bi-directional many-to-one association to UserAccount
		@ManyToOne
		@JoinColumn(name = "user_account_id")
		@MapsId("userAccountId")
		private UserAccount userAccount;
		
		@Column(name="write_permission")
		private boolean writePermission;
		
	    @Column(name = "serialized_properties")
	    private String serializedProperties;
		
		public ProjectUserAccountMap(){			
		}
		
		public ProjectUserAccountMapPK getId() {
			return this.id;
		}

		public void setId(ProjectUserAccountMapPK id) {
			this.id = id;
		}
		
		public Project getProject() {
			return project;
		}

		public void setProject(Project project) {
			this.project = project;
		}

		public UserAccount getUserAccount() {
			return userAccount;
		}

		public void setUserAccount(UserAccount userAccount) {
			this.userAccount = userAccount;
		}
		
		public boolean getWritePermission() {
			return this.writePermission;
		}

	    public void setWritePermission(final boolean writeAllowed) {
			this.writePermission= writeAllowed;
	    }
	    
	    public String getSerializedProperties() {
			return this.serializedProperties;
        }

        public void setSerializedProperties(String serializedProperties) {
			this.serializedProperties = serializedProperties;
        }

}
