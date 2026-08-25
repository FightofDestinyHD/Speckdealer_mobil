package com.speckdealer.app.update

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class RuntimeUpdateFlowThreadingTest {

	@Test
	fun runtimeUpdateFlow_downloadValidatedRelease_isSuspendFunction() {
		val method = RuntimeUpdateFlow::class.java.methods.first { it.name == "downloadValidatedRelease" }
		val parameterTypes = method.parameterTypes.map { it.name }
		assertFalse(parameterTypes.isEmpty())
		assertFalse(method.returnType == File::class.java)
	}
}
