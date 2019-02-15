package fr.proline.core.orm.uds;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * The primary key class for the project_user_account_map database table.
 * 
 */
@Embeddable
public class ProjectUserAccountMapPK implements Serializable {
	// default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name = "project_id")
	private long projectId;

	@Column(name = "user_account_id")
	private long userAccountId;

	public long getProjectId() {
		return projectId;
	}

	public void setProjectId(long projectId) {
		this.projectId = projectId;
	}

	public long getUserAccountId() {
		return userAccountId;
	}

	public void setUserAccountId(long userAccountId) {
		this.userAccountId = userAccountId;
	}

	@Override
	public boolean equals(final Object obj) {
		boolean result = false;

		if (obj == this) {
			result = true;
		} else if (obj instanceof ProjectUserAccountMapPK) {
			final ProjectUserAccountMapPK otherPK = (ProjectUserAccountMapPK) obj;

			result = ((getProjectId() == otherPK.getProjectId()) && (getUserAccountId() == otherPK.getUserAccountId()));
		}

		return result;

	}

	public int hashCode() {
		return (Long.valueOf(getProjectId()).hashCode() ^ Long.valueOf(getUserAccountId()).hashCode());
	}
}
