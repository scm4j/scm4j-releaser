package org.scm4j.releaser.scmactions.procs;

import org.scm4j.commons.Version;
import org.scm4j.commons.progress.IProgress;
import org.scm4j.releaser.CalculatedResult;
import org.scm4j.releaser.LogTag;
import org.scm4j.releaser.SCMReleaser;
import org.scm4j.releaser.branch.DevelopBranch;
import org.scm4j.releaser.branch.ReleaseBranch;
import org.scm4j.releaser.conf.Component;
import org.scm4j.vcs.api.IVCS;

public class SCMProcForkBranch implements ISCMProc {
	
	private final ReleaseBranch rb;
	private final DevelopBranch db;
	private final IVCS vcs;
	private final CalculatedResult calculatedResult;
	private final Component comp;
 
	public SCMProcForkBranch(ReleaseBranch rb, Component comp, CalculatedResult calculatedResult) {
		this.rb = rb;
		this.calculatedResult = calculatedResult;
		this.comp = comp;
		db = new DevelopBranch(comp);
		vcs = comp.getVCS();
	}
	
	@Override
	public void execute(IProgress progress) {
		createBranch(progress);
		
		truncateSnapshotReleaseVersion(progress);
		
		Version newTrunkVersion = bumpTrunkMinorVersion(progress);
		
		calculatedResult.replaceReleaseBranch(comp, new ReleaseBranch(comp, newTrunkVersion.toPreviousMinor().toReleaseZeroPatch(), true));
	}
	
	private void createBranch(IProgress progress) {
		String newBranchName = rb.getName();
		SCMReleaser.reportDuration(() -> vcs.createBranch(db.getName(), newBranchName, "release branch created"),
				"create branch " + newBranchName, null, progress);
	}
	
	private void truncateSnapshotReleaseVersion(IProgress progress) {
		String noSnapshotVersion = rb.getVersion().toString();
		String newBranchName = rb.getName();
		SCMReleaser.reportDuration(() -> vcs.setFileContent(newBranchName, SCMReleaser.VER_FILE_NAME, noSnapshotVersion, LogTag.SCM_VER + " " + noSnapshotVersion),
				"truncate snapshot: " + noSnapshotVersion + " in branch " + newBranchName, null, progress);
	}
	
	private Version bumpTrunkMinorVersion(IProgress progress) {
		Version newMinorVersion = db.getVersion().toNextMinor();
		SCMReleaser.reportDuration(() -> vcs.setFileContent(db.getName(), SCMReleaser.VER_FILE_NAME, newMinorVersion.toString(), LogTag.SCM_VER + " " + newMinorVersion),
				"change to version " + newMinorVersion + " in trunk", null, progress);
		return newMinorVersion;
	}
}
