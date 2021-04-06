package org.openmrs.module.cohortprograms.web.validator;

import org.openmrs.annotation.Handler;
import org.openmrs.module.cohortprograms.web.ProgramModel;
import org.openmrs.validator.ProgramValidator;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 4/1/21.
 */
@Handler(supports = ProgramModel.class, order = 60)
public class ProgramModelValidator implements Validator {
	
	private ProgramValidator programValidator = new ProgramValidator();
	
	@Override
	public boolean supports(Class<?> clazz) {
		return clazz.equals(ProgramModel.class);
	}
	
	@Override
	public void validate(Object target, Errors errors) {
		ProgramModel programModel = (ProgramModel) target;
		//		programValidator.validate(programModel.getProgram(), errors);
	}
}
