package org.scm4j.releaser;

import org.junit.Test;
import org.scm4j.releaser.branch.ReleaseBranchFactory;
import org.scm4j.releaser.conf.VCSRepository;

public class Coverage {

	@Test
	public void cover() {
		new Utils();
		new LogTag();
		new ReleaseBranchFactory();
		new VCSRepository("name", "url", null, null, null, null, null, null).toString();
	}
}