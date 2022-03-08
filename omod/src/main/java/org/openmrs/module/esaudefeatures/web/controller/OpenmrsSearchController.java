package org.openmrs.module.esaudefeatures.web.controller;

import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.esaudefeatures.web.ImportHelperService;
import org.openmrs.module.esaudefeatures.web.RemoteOpenmrsSearchDelegate;
import org.openmrs.module.esaudefeatures.web.RemoteOpenmrsSearchException;
import org.openmrs.module.esaudefeatures.web.Utils;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;

import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.IMPORTED_PATIENT_LOCATION_UUID_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_PASSWORD_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.REMOTE_SERVER_TYPE_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_URL_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_USERNAME_GP;
import static org.openmrs.util.OpenmrsConstants.OPENMRS_VERSION_SHORT;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 8/5/21.
 */
@Controller("esaudefeatures.remotePatientsController")
public class OpenmrsSearchController {
	
	private AdministrationService adminService;
	
	private RemoteOpenmrsSearchDelegate delegate;
	
	private ImportHelperService helperService;
	
	private PatientService patientService;
	
	private LocationService locationService;
	
	private static final Logger LOGGER = LoggerFactory.getLogger(OpenmrsSearchController.class);
	
	public static final String ROOT_PATH = "/module/esaudefeatures/findRemotePatients.form";
	
	public static final String ALT_ROOT_PATH = "/module/esaudefeatures/findRemotePatients.htm";
	
	@Autowired
	public void setAdminService(AdministrationService adminService) {
		this.adminService = adminService;
	}
	
	@Autowired
	public void setDelegate(RemoteOpenmrsSearchDelegate delegate) {
		this.delegate = delegate;
	}
	
	@Autowired
	public void setHelperService(ImportHelperService helperService) {
		this.helperService = helperService;
	}
	
	@Autowired
	public void setPatientService(PatientService patientService) {
		this.patientService = patientService;
	}
	
	@Autowired
	public void setLocationService(LocationService locationService) {
		this.locationService = locationService;
	}
	
	@RequestMapping(method = RequestMethod.GET, value = { ROOT_PATH, ALT_ROOT_PATH })
	public ModelAndView searchRemoteForm(ModelAndView modelAndView) {
		if (modelAndView == null) {
			modelAndView = new ModelAndView();
		}
		
		String remoteServerUrl = adminService.getGlobalProperty(OPENMRS_REMOTE_SERVER_URL_GP);
		if (StringUtils.hasText(remoteServerUrl)) {
			modelAndView.getModelMap().addAttribute("remoteServerUrl", remoteServerUrl);
		} else {
			LOGGER.warn("Global property {} not set", OPENMRS_REMOTE_SERVER_URL_GP);
		}
		
		modelAndView.getModelMap().addAttribute("remoteServerType",
		    adminService.getGlobalProperty(REMOTE_SERVER_TYPE_GP, "OPENMRS"));
		
		String remoteServerUsername = adminService.getGlobalProperty(OPENMRS_REMOTE_SERVER_USERNAME_GP);
		String remoteServerPassword = adminService.getGlobalProperty(OPENMRS_REMOTE_SERVER_PASSWORD_GP);
		if (StringUtils.hasText(remoteServerUsername) && StringUtils.hasText(remoteServerPassword)) {
			String remoteServerBasicAuth = new StringBuilder(remoteServerUsername).append(":").append(remoteServerPassword)
			        .toString();
			byte[] byteArray = remoteServerBasicAuth.getBytes();
			String base64encoded = Utils.byteArrayToBase64(remoteServerBasicAuth.getBytes(), 0, byteArray.length);
			modelAndView.getModelMap().addAttribute("remoteServerUsername", remoteServerUsername);
			modelAndView.getModelMap().addAttribute("remoteServerPassword", remoteServerPassword);
			modelAndView.getModelMap().addAttribute("remoteServerAuth", base64encoded);
		}
		
		if (StringUtils.isEmpty(remoteServerUsername)) {
			LOGGER.warn("Global property {} not set", OPENMRS_REMOTE_SERVER_USERNAME_GP);
		}
		
		if (StringUtils.isEmpty(remoteServerPassword)) {
			LOGGER.warn("Global property {} not set", OPENMRS_REMOTE_SERVER_PASSWORD_GP);
		}
		
		String importedPatientLocationUuid = adminService.getGlobalProperty(IMPORTED_PATIENT_LOCATION_UUID_GP);
		if (StringUtils.hasText(importedPatientLocationUuid)) {
			Location location = locationService.getLocationByUuid(importedPatientLocationUuid);
			if (location == null) {
				LOGGER.warn("The {} global property value is not valid because no location with {} exists in the system",
				    IMPORTED_PATIENT_LOCATION_UUID_GP, importedPatientLocationUuid);
			} else {
				modelAndView.getModelMap().addAttribute("importedPatientLocationUuid", importedPatientLocationUuid);
			}
		} else {
			LOGGER.warn("Global property {} not set", IMPORTED_PATIENT_LOCATION_UUID_GP);
		}
		
		// if there's an authenticated user, put them, and their patient set, in the model
		if (Context.getAuthenticatedUser() != null) {
			modelAndView.getModelMap().addAttribute("authenticatedUser", Context.getAuthenticatedUser());
		}
		
		if (OPENMRS_VERSION_SHORT.startsWith("1")) {
			modelAndView.setViewName("module/esaudefeatures/remotePatients/findRemotePatients1x");
		} else {
			modelAndView.setViewName("module/esaudefeatures/remotePatients/findRemotePatients2x");
		}
		return modelAndView;
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/module/esaudefeatures/openmrsRemotePatients.json", produces = { "application/json " })
	public SimpleObject remotePatientSearch(@RequestParam("text") String searchText) throws Exception {
		return delegate.searchPatients(searchText);
	}
	
	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(method = RequestMethod.POST, value = "/module/esaudefeatures/openmrsPatient.json")
	public String importPatient(@RequestParam("uuid") String patientUuid) {
		try {
			Patient patient = delegate.importPatientWithUuid(patientUuid);
			return patient.getPatientId().toString();
		}
		catch (Exception e) {
			LOGGER.error("An error occured while importing patient with uuid {}", patientUuid, e);
			if (e instanceof RemoteOpenmrsSearchException) {
				throw (RemoteOpenmrsSearchException) e;
			} else {
				throw new RemoteOpenmrsSearchException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			}
		}
	}
}