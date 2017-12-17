package org.scm4j.releaser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scm4j.commons.Version;
import org.scm4j.releaser.actions.IAction;
import org.scm4j.releaser.branch.ReleaseBranchCurrent;
import org.scm4j.releaser.branch.ReleaseBranchFactory;
import org.scm4j.releaser.branch.ReleaseBranchPatch;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.DelayedTagsFile;
import org.scm4j.releaser.conf.TagDesc;
import org.scm4j.vcs.api.VCSTag;
import org.scm4j.vcs.api.WalkDirection;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class WorkflowDelayedTagTest extends WorkflowTestBase {

	private final DelayedTagsFile dtf = new DelayedTagsFile();
	
	@Before
	@After
	public void setUpTearDown() {
		dtf.delete();
	}

	@Test
	public void testDelayedTagOnPatch() throws Exception {
		// build all
		IAction action = getActionTreeBuild(compUnTill);
		assertIsGoingToForkAndBuildAll(action);
		execAction(action);

		// add feature to unTillDb release/2.59
		Component compUnTillDbVersioned = compUnTillDb.clone(env.getUnTillDbVer());
		ReleaseBranchPatch rb = ReleaseBranchFactory.getReleaseBranchPatch(compUnTillDbVersioned);
		env.generateFeatureCommit(env.getUnTillDbVCS(), rb.getName(), "patch feature merged");
		
		// build all patches, delayed tag
		Component compUnTillVersioned = compUnTill.clone(env.getUnTillVer().toReleaseZeroPatch());
		action = getActionDelayedTag(compUnTillVersioned); //actionBuilder.getActionTreeDelayedTag(compUnTillVersioned);
		assertIsGoingToBuildAll(action);
		execAction(action);
		
		// check no new tags
		assertEquals(2, env.getUblVCS().getTags().size());
		assertEquals(2, env.getUnTillDbVCS().getTags().size());
		assertEquals(1, env.getUnTillVCS().getTags().size());
		
		// check Delayed Tags file
		assertNull(dtf.getRevisitonByUrl(compUnTillDb.getVcsRepository().getUrl()));
		assertNotNull(dtf.getRevisitonByUrl(compUnTill.getVcsRepository().getUrl()));
		assertNull(dtf.getRevisitonByUrl(compUBL.getVcsRepository().getUrl()));

		// check Delayed Tags are used
		action = getActionTreeBuild(compUnTillVersioned);
		assertIsGoingToDoNothing(action);
	}
	
	@Test
	public void testDelayedTag() throws Exception {
		IAction action = getActionDelayedTag(compUnTill);
		execAction(action);

		// check root component tag is delayed
		assertTrue(env.getUnTillVCS().getTags().isEmpty());
		assertTrue(env.getUnTillDbVCS().getTags().size() == 1);
		assertTrue(env.getUblVCS().getTags().size() == 1);
		
		// check component with delayed tag is considered as tagged (DONE) on build
		action = getActionTreeBuild(compUnTill);
		assertIsGoingToDoNothing(action);

		// check Delayed Tags file
		assertNull(dtf.getRevisitonByUrl(compUnTillDb.getVcsRepository().getUrl()));
		assertNotNull(dtf.getRevisitonByUrl(compUnTill.getVcsRepository().getUrl()));
		assertNull(dtf.getRevisitonByUrl(compUBL.getVcsRepository().getUrl()));

		// create tag which was delayed
		action = getActionTreeTag(compUnTill);
		assertIsGoingToTag(action, compUnTill);
		
		execAction(action);

		// check tags
		assertTrue(isPreHeadCommitTaggedWithVersion(compUBL));
		assertTrue(isPreHeadCommitTaggedWithVersion(compUnTillDb));
		assertTrue(isPreHeadCommitTaggedWithVersion(compUnTill));

		// check Dealyed Tags file
		assertTrue(dtf.getContent().isEmpty());
	}

	@Test
	public void testTagFileDeleted() throws Exception {
		// build all, root tag delayed
		IAction action = getActionDelayedTag(compUnTill);
		execAction(action);

		// simulate delayed tags file is deleted right after action create
		action = getActionTreeTag(compUnTill);
		assertIsGoingToTag(action, compUnTill);
		dtf.delete();
		execAction(action);

		// check no tags
		assertTrue(env.getUnTillVCS().getTags().isEmpty());
		assertFalse(env.getUnTillDbVCS().getTags().isEmpty());
		assertFalse(env.getUblVCS().getTags().isEmpty());
	}

	@Test
	public void testTagExistsOnExecute() throws Exception {
		// build all
		IAction action = getActionDelayedTag(compUnTill);
		execAction(action);

		// all is going to tag
		action = getActionTreeTag(compUnTill);
		assertIsGoingToTag(action, compUnTill);

		// simulate tag exists already
		ReleaseBranchCurrent rb = ReleaseBranchFactory.getCRB(compUnTill);
		Map<String, String> content = dtf.getContent();
		for (Map.Entry<String, String> entry : content.entrySet()) {
			if (compUnTill.getVcsRepository().getUrl().equals(entry.getKey())) {
				Version delayedTagVersion = new Version(env.getUnTillVCS().getFileContent(rb.getName(), Utils.VER_FILE_NAME,
						entry.getValue()));
				TagDesc tagDesc = Utils.getTagDesc(delayedTagVersion.toString());
				env.getUnTillVCS().createTag(rb.getName(), tagDesc.getName(), tagDesc.getMessage(), entry.getValue());
			}
		}

		Thread.sleep(1000); // FIXME: test fails without sleep
		// tagging should be skipped with no exceptions
		execAction(action);

		// check tags
		assertTrue(isPreHeadCommitTaggedWithVersion(compUBL));
		assertTrue(isPreHeadCommitTaggedWithVersion(compUnTillDb));
		assertTrue(isPreHeadCommitTaggedWithVersion(compUnTill));

		// check Dealyed Tags file
		assertTrue(dtf.getContent().isEmpty());
	}
	
	@Test
	public void testDoNothingIfNoDelayedTags() {
		IAction action = getActionTreeTag(compUnTillDb);
		assertIsGoingToTag(action, compUnTillDb);
		execAction(action);

		// check no tags
		assertTrue(env.getUnTillVCS().getTags().isEmpty());
		assertTrue(env.getUnTillDbVCS().getTags().isEmpty());
		assertTrue(env.getUblVCS().getTags().isEmpty());
	}
	
	@Test
	public void testTagExistsOnGetActionTree() throws Exception {
		// build all
		IAction action = getActionDelayedTag(compUnTillDb);
		assertIsGoingToForkAndBuild(action, compUnTillDb);
		execAction(action);

		String revisionToTag = dtf.getRevisitonByUrl(compUnTillDb.getVcsRepository().getUrl());
		ReleaseBranchCurrent rb = ReleaseBranchFactory.getCRB(compUnTillDb);
		env.getUnTillDbVCS().createTag(rb.getName(), "other-tag", "other tag message", revisionToTag);
		
		// simulate tag exists
		Version delayedTagVersion = new Version(env.getUnTillDbVCS().getFileContent(rb.getName(), Utils.VER_FILE_NAME,
				revisionToTag));
		TagDesc tagDesc = Utils.getTagDesc(delayedTagVersion.toString());
		env.getUnTillDbVCS().createTag(rb.getName(), tagDesc.getName(), tagDesc.getMessage(), revisionToTag);

		Thread.sleep(1000); // FIXME: test fails without sleep

		// check version tag is detected -> tagging skipped
		action = getActionTreeTag(compUnTillDb);
		assertIsGoingToTag(action, compUnTillDb);
		execAction(action);

		// check no new tags
		assertTrue(env.getUnTillDbVCS().getTags().size() == 2);
	}
	
	private boolean isPreHeadCommitTaggedWithVersion(Component comp) {
		ReleaseBranchCurrent rb = ReleaseBranchFactory.getCRB(comp);
		List<VCSTag> tags = comp.getVCS().getTagsOnRevision(comp.getVCS().getCommitsRange(rb.getName(), null, WalkDirection.DESC, 2).get(1).getRevision());
		for (VCSTag tag : tags) {
			if (tag.getTagName().equals(rb.getVersion().toPreviousPatch().toReleaseString())) {
				return true;
			}
		}
		return false;
	}
}


