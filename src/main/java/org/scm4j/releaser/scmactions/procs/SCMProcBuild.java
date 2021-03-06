package org.scm4j.releaser.scmactions.procs;

import lombok.SneakyThrows;
import org.scm4j.commons.Version;
import org.scm4j.commons.progress.IProgress;
import org.scm4j.releaser.*;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.DelayedTagsFile;
import org.scm4j.releaser.conf.TagDesc;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.exceptions.ENoBuilder;
import org.scm4j.releaser.exceptions.ENoReleaseBranch;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSCommit;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

public class SCMProcBuild implements ISCMProc {
	
	private final IVCS vcs;
	private final Component comp;
	private final String releaseBranchName;
	private final Version versionToBuild;
	private final CachedStatuses cache;
	private final boolean delayedTag;
	private final VCSRepository repo;
 
	public SCMProcBuild(Component comp, CachedStatuses cache, boolean delayedTag, VCSRepository repo) {
		this.comp = comp;
		this.repo = repo;
		vcs = repo.getVCS();
		this.cache = cache;
		releaseBranchName = Utils.getReleaseBranchName(repo, cache.get(repo.getUrl()).getNextVersion());
		versionToBuild = cache.get(repo.getUrl()).getNextVersion();
		this.delayedTag = delayedTag;
	}

	@Override
	public void execute(IProgress progress) {
		VCSCommit headCommit = vcs.getHeadCommit(releaseBranchName);
		if (headCommit == null) {
			throw new ENoReleaseBranch(releaseBranchName);
		}
		
		if (repo.getBuilder() == null) {
			throw new ENoBuilder(comp);
		}
		
		build(progress, headCommit);
		
		tagBuild(progress, headCommit);

		if (!delayedTag) {
			raisePatchVersion(progress);
		}
		
		ExtendedStatus existing = cache.get(repo.getUrl());
		cache.replace(repo.getUrl(), new ExtendedStatus(versionToBuild.toNextPatch(), existing.getStatus(),
				existing.getSubComponents(), comp, repo));
		
		progress.reportStatus(comp.getName() + " " + versionToBuild + " is built in " + releaseBranchName);
	}
	
	@SneakyThrows
	private void build(IProgress progress, VCSCommit headCommit) {
		File buildDir = Utils.getBuildDir(repo, versionToBuild);
		if (buildDir.exists()) {
			Utils.waitForDeleteDir(buildDir);
		}
		Files.createDirectories(buildDir.toPath());

		String statusMessage = String.format(" out %s on revision %s into %s", comp.getName(), headCommit.getRevision(), buildDir.getPath());
		progress.reportStatus("checking" + statusMessage + "...");
		Utils.reportDuration(() -> vcs.checkout(releaseBranchName, buildDir.getPath(), headCommit.getRevision()), "checked" + statusMessage, null, progress);
		Map<String, String> btev = Utils.getBuildTimeEnvVars(repo.getType(), headCommit.getRevision(), releaseBranchName,
				repo.getUrl());
		repo.getBuilder().build(comp, buildDir, progress, btev);
	}

	@SneakyThrows
	private void tagBuild(IProgress progress, VCSCommit headCommit) {
		if (delayedTag) {
			DelayedTagsFile delayedTagsFile = new DelayedTagsFile();
			delayedTagsFile.writeUrlDelayedTag(repo.getUrl(), versionToBuild, headCommit.getRevision());
			progress.reportStatus("build commit " + headCommit.getRevision() + " is saved for delayed tagging");
		} else {
			TagDesc tagDesc = Utils.getTagDesc(versionToBuild.toString());
			Utils.reportDuration(() -> vcs.createTag(releaseBranchName, tagDesc.getName(), tagDesc.getMessage(), headCommit.getRevision()),
					String.format("tag head of %s: %s", releaseBranchName, tagDesc.getName()), null, progress);
		}
	}

	private void raisePatchVersion(IProgress progress) {
		Version nextPatchVersion = versionToBuild.toNextPatch();
		Utils.reportDuration(() -> vcs.setFileContent(releaseBranchName, Constants.VER_FILE_NAME, nextPatchVersion.toString(),
				Constants.SCM_VER + " " + nextPatchVersion),
				"bump patch version in release branch: " + nextPatchVersion, null, progress);
	}
}
