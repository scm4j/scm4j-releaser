package org.scm4j.releaser.scmactions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.mockito.Mockito;
import org.scm4j.commons.progress.ProgressConsole;
import org.scm4j.releaser.SCMReleaser;
import org.scm4j.releaser.TestEnvironment;
import org.scm4j.releaser.WorkflowTestBase;
import org.scm4j.releaser.actions.ActionKind;
import org.scm4j.releaser.actions.IAction;
import org.scm4j.releaser.branch.ReleaseBranch;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.VCSRepository;
import org.scm4j.releaser.exceptions.ENoBuilder;
import org.scm4j.releaser.exceptions.EReleaserException;
import org.scm4j.releaser.scmactions.procs.ISCMProc;
import org.scm4j.releaser.scmactions.procs.SCMProcBuild;

public class SCMProcBuildTest extends WorkflowTestBase {
	
	@Test
	public void testNoReleaseBranch() {
		ReleaseBranch rb = new ReleaseBranch(compUBL);
		ISCMProc proc = new SCMProcBuild(rb);
		try {
			proc.execute(new ProgressConsole());
			fail();
		} catch (EReleaserException e) {
			
		}
	}
	
	@Test
	public void testNoBuilder() throws Exception {
		Component mockedComp = Mockito.spy(new Component(TestEnvironment.PRODUCT_UNTILL));
		VCSRepository mockedRepo = Mockito.spy(mockedComp.getVcsRepository());
		Mockito.when(mockedComp.getVcsRepository()).thenReturn(mockedRepo);
		Mockito.when(mockedRepo.getBuilder()).thenReturn(null);
		IAction action = new SCMReleaser().getActionTree(mockedComp, ActionKind.FORK_ONLY);
		action.execute(new ProgressConsole());
		ISCMProc proc = new SCMProcBuild(new ReleaseBranch(mockedComp));
		try {
			proc.execute(new ProgressConsole());
			fail();
		} catch (ENoBuilder e) {
			assertEquals(mockedComp, e.getComp());
		}
	}
}
