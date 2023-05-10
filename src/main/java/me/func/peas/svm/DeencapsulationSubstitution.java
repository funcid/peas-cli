package me.func.peas.svm;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import me.func.peas.Deencapsulation;

@TargetClass(Deencapsulation.class)
public final class DeencapsulationSubstitution {
	@Substitute
	public static void init() {}
}
