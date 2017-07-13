package org.scm4j.wf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.workingcopy.IVCSWorkspace;
import org.scm4j.wf.actions.ActionError;
import org.scm4j.wf.actions.ActionNone;
import org.scm4j.wf.actions.IAction;
import org.scm4j.wf.conf.Dep;
import org.scm4j.wf.conf.VCSRepositories;
import org.scm4j.wf.conf.VCSRepository;
import org.scm4j.wf.conf.VCSType;
import org.scm4j.wf.conf.Version;
import org.scm4j.wf.exceptions.EDepConfig;

@PrepareForTest(VCSFactory.class)
@RunWith(PowerMockRunner.class)
public class SCMWorkflowGetActionTest {

	private static final String TEST_DEP = "test:dep";
	private static final String TEST_MASTER_BRANCH = "test master branch";
	private static final VCSCommit COMMIT_FEATURE = new VCSCommit("test revision", "feature commit", null);
	private static final VCSCommit COMMIT_VER = new VCSCommit("test revision", SCMActionProductionRelease.VCS_TAG_SCM_VER, null);
	private static final VCSCommit COMMIT_IGNORE = new VCSCommit("test revision", SCMActionProductionRelease.VCS_TAG_SCM_IGNORE, null);
	
	@Mock
	IVCS mockedVcs;
	
	@Mock
	VCSRepositories mockedRepos;

	@Mock
	IVCSWorkspace ws;
	
	private VCSRepository testRepo;
	private SCMWorkflow wf;
	
	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		testRepo = new VCSRepository();
		testRepo.setDevBranch(TEST_MASTER_BRANCH);
		testRepo.setName(TEST_DEP);
		testRepo.setType(VCSType.GIT);
		Mockito.doReturn(testRepo).when(mockedRepos).get(testRepo.getName());
		PowerMockito.mockStatic(VCSFactory.class);
		PowerMockito.when(VCSFactory.getIVCS(testRepo, ws)).thenReturn(mockedVcs);
		wf = new SCMWorkflow(new Dep(TEST_DEP, testRepo), mockedRepos, ws);
		Mockito.doReturn(true).when(mockedVcs).fileExists(testRepo.getDevBranch(), SCMWorkflow.VER_FILE_NAME);
	}
	
	@Test
	public void testProductionReleaseNewFeatures() {
		Mockito.doReturn(COMMIT_FEATURE).when(mockedVcs).getHeadCommit(TEST_MASTER_BRANCH);
		IAction action = wf.getProductionReleaseActionRoot(new ArrayList<IAction>());
		assertTrue(action instanceof SCMActionProductionRelease);
		SCMActionProductionRelease r = (SCMActionProductionRelease) action;
		assertEquals(r.getReason(), ProductionReleaseReason.NEW_FEATURES);
	}
	
	@Test 
	public void testActionNoneIfNoVersionFile() {
		Mockito.doReturn(false).when(mockedVcs).fileExists(testRepo.getDevBranch(), SCMWorkflow.VER_FILE_NAME);
		Mockito.doReturn(COMMIT_FEATURE).when(mockedVcs).getHeadCommit(TEST_MASTER_BRANCH);
		try {
			wf.getProductionReleaseActionRoot(new ArrayList<IAction>());
			fail();
		} catch (EDepConfig e) {
			
		}
	}
	
	@Test
	public void testActionNoneIfHasErrors() {
		List<Dep> testMDeps = Collections.singletonList(new Dep(TEST_DEP + ":1.0.0", mockedRepos));
		wf.setMDeps(testMDeps);
		List<IAction> childActions = new ArrayList<>();
		childActions.add(new ActionError(new Dep(TEST_DEP + ":1.0.0", mockedRepos), Collections.<IAction>emptyList(), TEST_MASTER_BRANCH, "test error cause", ws));
		
		IAction res = wf.getProductionReleaseActionRoot(childActions);
		assertTrue(res instanceof ActionNone);
	}
	
	@Test
	public void testProductionReleaseNewDependencies() {
		Mockito.doReturn("0.0.0").when(mockedVcs).getFileContent(TEST_MASTER_BRANCH, SCMWorkflow.VER_FILE_NAME);
		Mockito.doReturn(COMMIT_VER).when(mockedVcs).getHeadCommit(TEST_MASTER_BRANCH);
		List<Dep> testMDeps = Collections.singletonList(new Dep(TEST_DEP + ":1.0.0", mockedRepos));
		wf.setMDeps(testMDeps);
		List<IAction> childActions = new ArrayList<>();
		childActions.add(new SCMActionUseLastReleaseVersion(new Dep(TEST_DEP + ":1.0.0", mockedRepos), Collections.<IAction>emptyList(), TEST_MASTER_BRANCH, ws));
		IAction action = wf.getProductionReleaseActionRoot(childActions);
		assertTrue(action instanceof SCMActionProductionRelease);
		SCMActionProductionRelease pr = (SCMActionProductionRelease) action;
		assertEquals(pr.getReason(), ProductionReleaseReason.NEW_DEPENDENCIES);
	}

	@Test
	public void testUseLastReleaseIfNoFeatures() {
		Mockito.doReturn(COMMIT_VER).when(mockedVcs).getHeadCommit(TEST_MASTER_BRANCH);
		Version ver = new Version("1.0.0");
		Mockito.doReturn("1.0.0").when(mockedVcs).getFileContent(TEST_MASTER_BRANCH, SCMWorkflow.VER_FILE_NAME);
		IAction action = wf.getProductionReleaseActionRoot(new ArrayList<IAction>());
		assertTrue(action instanceof SCMActionUseLastReleaseVersion);
		SCMActionUseLastReleaseVersion lastRelease = (SCMActionUseLastReleaseVersion) action;
		assertTrue(lastRelease.getVer().equals(ver));
	}

	@Test
	public void testUseLastReleaseIfIgnore() {
		Mockito.doReturn(COMMIT_IGNORE).when(mockedVcs).getHeadCommit(TEST_MASTER_BRANCH);
		Version ver = new Version("1.0.0");
		Mockito.doReturn("1.0.0").when(mockedVcs).getFileContent(TEST_MASTER_BRANCH, SCMWorkflow.VER_FILE_NAME);
		IAction action = wf.getProductionReleaseActionRoot(new ArrayList<IAction>());
		assertTrue(action instanceof SCMActionUseLastReleaseVersion);
		SCMActionUseLastReleaseVersion lastRelease = (SCMActionUseLastReleaseVersion) action;
		assertTrue(lastRelease.getVer().equals(ver));
	}
}