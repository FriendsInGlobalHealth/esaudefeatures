package org.openmrs.module.esaudefeatures.web;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.openmrs.Auditable;
import org.openmrs.BaseOpenmrsMetadata;
import org.openmrs.Concept;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.Person;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.User;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.UserService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_PASSWORD_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_URL_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_USERNAME_GP;
import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.REMOTE_SERVER_SKIP_HOSTNAME_VERIFICATION_GP;
import static org.openmrs.module.esaudefeatures.web.Utils.parseDateString;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 2/20/22.
 */
@Component
public class ImportHelperService {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ImportHelperService.class);
	
	private ConceptService conceptService;
	
	private AdministrationService adminService;
	
	private LocationService locationService;
	
	private PatientService patientService;
	
	private PersonService personService;
	
	private UserService userService;

	private ConcurrentMap<String, User> importedUsersCache = new ConcurrentHashMap<String, User>();

	private static int importUserCalls = 0;
	private static int updateAuditInfoCalls = 0;
	
	public static final List<String> IGNORED_PERSON_ATTRIBUTE_TYPES = new ArrayList<String>();
	
	static {
		// Health center is not imported to allow for a new value to be propagated.
		IGNORED_PERSON_ATTRIBUTE_TYPES.add("8d87236c-c2cc-11de-8d13-0010c6dffd0f");
	}
	
	@Autowired
	public void setConceptService(ConceptService conceptService) {
		this.conceptService = conceptService;
	}
	
	@Autowired
	public void setAdminService(AdministrationService adminService) {
		this.adminService = adminService;
	}
	
	@Autowired
	public void setPatientService(PatientService patientService) {
		this.patientService = patientService;
	}
	
	@Autowired
	public void setPersonService(PersonService personService) {
		this.personService = personService;
	}
	
	@Autowired
	public void setLocationService(LocationService locationService) {
		this.locationService = locationService;
	}
	
	@Autowired
	public void setUserService(UserService userService) {
		this.userService = userService;
	}

	public Patient getPatientFromOpenmrsRestPayload(SimpleObject patientObject) throws Exception {
		Person person = getPersonFromOpenmrsRestRepresentation((Map) patientObject.get("person"));
		Patient patient = new Patient(person);
		Map auditInfo = patientObject.get("auditInfo");
		
		if (patientObject.containsKey("identifiers") && patientObject.get("identifiers") != null) {
			List<Map> identifiersMaps = (List<Map>) patientObject.get("identifiers");
			Set<PatientIdentifier> identifiers = new TreeSet<PatientIdentifier>();
			
			for (Map identifierMap : identifiersMaps) {
				if (!(Boolean) identifierMap.get("voided")) {
					PatientIdentifier identifier = new PatientIdentifier();
					identifier.setUuid((String) identifierMap.get("uuid"));
					identifier.setIdentifier((String) identifierMap.get("identifier"));
					identifier.setIdentifierType(patientService.getPatientIdentifierTypeByUuid((String) ((Map) identifierMap
					        .get("identifierType")).get("uuid")));
					if (identifierMap.containsKey("location") && identifierMap.get("location") != null) {
						String idLocUuid = (String) ((Map) identifierMap.get("location")).get("uuid");
						Location idLocation = locationService.getLocationByUuid(idLocUuid);
						
						if (idLocation == null) {
							idLocation = importLocationFromRemoteOpenmrsServer(idLocUuid);
						}
						identifier.setLocation(idLocation);
					}
					identifier.setPreferred((Boolean) identifierMap.get("preferred"));
					identifier.setPatient(patient);
					identifiers.add(identifier);
				}
			}
			patient.setIdentifiers(identifiers);
		}
		
		updateAuditInfo(patient, auditInfo);
		return patient;
	}
	
	public Person getPersonFromOpenmrsRestRepresentation(Map personMap) {
		Person person = new Person();
		person.setUuid((String) personMap.get("uuid"));
		person.setGender((String) personMap.get("gender"));
		person.setBirthdate(parseDateString((String) personMap.get("birthdate")));
		person.setBirthdateEstimated((Boolean) personMap.get("birthdateEstimated"));
		person.setDead((Boolean) personMap.get("dead"));
		String deathDateString = (String) personMap.get("deathDate");
		if (deathDateString != null) {
			person.setDeathDate(parseDateString(deathDateString));
		}
		
		Object causeOfDeath = personMap.get("causeOfDeath");
		if (causeOfDeath != null) {
			String conceptUuid = null;
			if (causeOfDeath instanceof Map) {
				conceptUuid = (String) ((Map) causeOfDeath).get("uuid");
			} else if (causeOfDeath instanceof String) {
				conceptUuid = (String) causeOfDeath;
			}
			
			if (conceptUuid != null) {
				Concept causeOfDeathConcept = conceptService.getConceptByUuid(conceptUuid);
				person.setCauseOfDeath(causeOfDeathConcept);
			}
		}
		
		// Names
		Set<PersonName> personNames = new TreeSet<PersonName>();
		String preferredNameUuid = null;
		if (personMap.containsKey("preferredName")) {
			Map preferredName = (Map) personMap.get("preferredName");
			preferredNameUuid = (String) preferredName.get("uuid");
			PersonName preferredPersonName = new PersonName();
			preferredPersonName.setUuid(preferredNameUuid);
			preferredPersonName.setGivenName((String) preferredName.get("givenName"));
			preferredPersonName.setMiddleName((String) preferredName.get("middleName"));
			preferredPersonName.setFamilyName((String) preferredName.get("familyName"));
			preferredPersonName.setPreferred(true);
			personNames.add(preferredPersonName);
		}
		
		if (personMap.containsKey("names") && personMap.get("names") != null) {
			List<Map> names = (List<Map>) personMap.get("names");
			for (Map name : names) {
				String nameUuid = (String) name.get("uuid");
				if (nameUuid.equals(preferredNameUuid)) {
					continue;
				}
				if (!(Boolean) name.get("voided")) {
					PersonName personName = new PersonName();
					personName.setUuid((String) name.get("uuid"));
					personName.setGivenName((String) name.get("givenName"));
					personName.setMiddleName((String) name.get("middleName"));
					personName.setFamilyName((String) name.get("familyName"));
					personNames.add(personName);
				}
			}
		}
		
		person.setNames(personNames);
		
		if (personMap.containsKey("addresses") && personMap.get("addresses") != null) {
			List<Map> addressesMaps = (List<Map>) personMap.get("addresses");
			Set<PersonAddress> personAddresses = new TreeSet<PersonAddress>();
			Set<String> allKeys = addressesMaps.get(0).keySet();
			allKeys.removeAll(Arrays.asList("display", "links", "resourceVersion"));
			
			for (Map addressMap : addressesMaps) {
				PersonAddress personAddress = new PersonAddress();
				for (String key : allKeys) {
					try {
						Field field = PersonAddress.class.getField(key);
						field.setAccessible(true);
						field.set(personAddress, addressMap.get(key));
						field.setAccessible(false);
					}
					catch (NoSuchFieldException e) {
						// Ignore the field.
					}
					catch (IllegalAccessException e) {
						// Ignore this as well.
					}
				}
				personAddresses.add(personAddress);
			}
			person.setAddresses(personAddresses);
		}
		
		if (personMap.containsKey("attributes") && personMap.get("attributes") != null
		        && ((List) personMap.get("attributes")).size() > 0) {
			List<Map> attributesMaps = (List<Map>) personMap.get("attributes");
			Set<PersonAttribute> personAttributes = new TreeSet<PersonAttribute>();
			
			for (Map attributeMap : attributesMaps) {
				String personAttributeTypeUuid = (String) ((Map) attributeMap.get("attributeType")).get("uuid");
				if (IGNORED_PERSON_ATTRIBUTE_TYPES.contains(personAttributeTypeUuid)) {
					continue;
				}
				
				if (!(Boolean) attributeMap.get("voided")) {
					PersonAttribute personAttribute = new PersonAttribute();
					personAttribute.setUuid((String) attributeMap.get("uuid"));
					
					PersonAttributeType personAttributeType = personService
					        .getPersonAttributeTypeByUuid(personAttributeTypeUuid);
					personAttribute.setAttributeType(personAttributeType);
					
					Object attributeValue = attributeMap.get("value");
					if (attributeValue instanceof String) {
						personAttribute.setValue((String) attributeValue);
					} else if (attributeValue instanceof Map) {
						if ("org.openmrs.Concept".equals(personAttributeType.getFormat())) {
							Concept concept = conceptService.getConceptByUuid((String) ((Map) attributeValue).get("uuid"));
							personAttribute.setValue(concept.getConceptId().toString());
						} else if ("org.openmrs.Location".equals(personAttributeType.getFormat())) {
							// Location is not harmonized hence some work needs to be done here.
							String locationUuid = (String) ((Map) attributeValue).get("uuid");
							Location location = locationService.getLocationByUuid(locationUuid);
							if (location == null) {
								// Import this location from central (possibly including its ancestors)
								location = importLocationFromRemoteOpenmrsServer(locationUuid);
							}
							personAttribute.setValue(location.getLocationId().toString());
						} else {
							// We gonna go on a limb and set the uuid (assuming it is openmrs domain object) as is (What could happen here?) (._.)
							personAttribute.setValue((String) ((Map) attributeValue).get("uuid"));
						}
					}
					personAttributes.add(personAttribute);
				}
			}
			person.setAttributes(personAttributes);
		}
		return person;
	}
	
	public void updateAuditInfo(Auditable openmrsObject, Map<String, Object> auditInfo) {
		System.out.println("updateAuditInfo Call Count is " + (++updateAuditInfoCalls));
		Map creatorMap = (Map) auditInfo.get("creator");
		if (creatorMap != null) {
			String creatorUuid = (String) creatorMap.get("uuid");
			User creator = userService.getUserByUuid(creatorUuid);

			if (creator == null) {
				creator = importUserFromRemoteOpenmrsServer(creatorUuid);
			}
			openmrsObject.setCreator(creator);
			openmrsObject.setDateCreated(parseDateString((String) auditInfo.get("dateCreated")));
		}

		Map changerMap = (Map) auditInfo.get("changedBy");
		if (changerMap != null) {
			String changerUuid = (String) changerMap.get("uuid");
			User changer = userService.getUserByUuid(changerUuid);

			if (changer == null) {
				changer = importUserFromRemoteOpenmrsServer(changerUuid);
			}
			openmrsObject.setChangedBy(changer);
			openmrsObject.setDateChanged(parseDateString((String) auditInfo.get("dateChanged")));
		}

		if(openmrsObject instanceof BaseOpenmrsMetadata) {
			Map retireeMap = (Map) auditInfo.get("retiredBy");
			if (retireeMap != null) {
				BaseOpenmrsMetadata metadata = (BaseOpenmrsMetadata) openmrsObject;
				String retirerUuid = (String) retireeMap.get("uuid");
				User retirer = userService.getUserByUuid(retirerUuid);

				if (retirer == null) {
					retirer = importUserFromRemoteOpenmrsServer(retirerUuid);
				}
				metadata.setRetiredBy(retirer);
				metadata.setRetired(true);
				metadata.setRetireReason((String) retireeMap.get("retireReason"));
				metadata.setDateRetired(parseDateString((String) auditInfo.get("dateRetired")));
			}
		}
	}

	public User importUserFromRemoteOpenmrsServer(String userUuid) {
		if(importedUsersCache.containsKey(userUuid)) {
			return importedUsersCache.get(userUuid);
		}
		String[] urlUserPass = getRemoteOpenmrsHostUsernamePassword();
		String errorMessage = String.format("Could not fetch user with uuid %s from server %s", userUuid, urlUserPass[0]);
		String[] pathSegments = { "ws/rest/v1/user", userUuid };
		System.out.println("importUserFromRemoteOpenmrsServer Call Count is " + (++importUserCalls));

		Map<String, String> queryParams = new HashMap<String, String>();
		queryParams.put("v", "full");

		Request userRequest = Utils.createBasicAuthGetRequest(urlUserPass[0], urlUserPass[1], urlUserPass[2], pathSegments, queryParams);
		boolean skipHostnameVerification = Boolean.parseBoolean(adminService.getGlobalProperty(
		    REMOTE_SERVER_SKIP_HOSTNAME_VERIFICATION_GP, "FALSE"));

		OkHttpClient httpClient;
		try {
			httpClient = Utils.createOkHttpClient(skipHostnameVerification);
		}
		catch (Exception e) {
			LOGGER.error("Could not create an http client", null, e);
			throw new RemoteOpenmrsSearchException(errorMessage, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		
		Response response;
		try {
			response = httpClient.newCall(userRequest).execute();
			if (response.isSuccessful() && response.code() == HttpServletResponse.SC_OK) {
				SimpleObject fetchedUser = SimpleObject.parseJson(response.body().string());
				User user = new User();
				user.setUuid((String) fetchedUser.get("uuid"));
				user.setSystemId((String) fetchedUser.get("systemId"));
				user.setUsername((String) fetchedUser.get("username"));
				
				Person person = personService.getPersonByUuid((String) ((Map) fetchedUser.get("person")).get("uuid"));
				if (person == null) {
					person = getPersonFromOpenmrsRestRepresentation((Map) fetchedUser.get("person"));
					person = personService.savePerson(person);
				}
				user.setPerson(person);
				
				if (fetchedUser.containsKey("userProperties") && fetchedUser.get("userProperties") != null) {
					user.setUserProperties((Map) fetchedUser.get("userProperties"));
				}

				importedUsersCache.put(userUuid, user);
				updateAuditInfo(user, (Map) fetchedUser.get("auditInfo"));
				user = userService.createUser(user, "aldkldlala8040202dfewdaddl23423");
				return user;
			}
		}
		catch (IOException e) {
			LOGGER.error("Could not execute the http request", null, e);
			throw new RemoteOpenmrsSearchException(errorMessage, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		throw new RemoteOpenmrsSearchException(errorMessage, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	}
	
	public Location importLocationFromRemoteOpenmrsServer(String locationUuid) {
		String[] urlUserPass = getRemoteOpenmrsHostUsernamePassword();
		String message = String.format("Could not fetch location with uuid %s from server %s", locationUuid, urlUserPass[0]);
		String[] locationPathSegments = { "ws/rest/v1/location", locationUuid };
		Map<String, String> queryParams = new HashMap<String, String>();
		queryParams.put("v", "full");
		
		Request locRequest = Utils.createBasicAuthGetRequest(urlUserPass[0], urlUserPass[1], urlUserPass[2],
		    locationPathSegments, queryParams);
		boolean skipHostnameVerification = Boolean.parseBoolean(adminService.getGlobalProperty(
		    REMOTE_SERVER_SKIP_HOSTNAME_VERIFICATION_GP, "FALSE"));
		OkHttpClient httpClient;
		try {
			httpClient = Utils.createOkHttpClient(skipHostnameVerification);
		}
		catch (Exception e) {
			LOGGER.error("Could not create http client");
			throw new RemoteOpenmrsSearchException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		
		Response response;
		try {
			response = httpClient.newCall(locRequest).execute();
		}
		catch (IOException e) {
			LOGGER.error(
			    String.format("Error when fetching location with uuid \"%s\" from server %s", locationUuid, urlUserPass[0]), e);
			throw new RemoteOpenmrsSearchException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		
		if (response.isSuccessful() && response.code() == HttpServletResponse.SC_OK) {
			final SimpleObject fetchedLocationObject;
			try {
				fetchedLocationObject = SimpleObject.parseJson(response.body().string());
			}
			catch (IOException e) {
				LOGGER.error(String.format("Error while reading response from server %s", urlUserPass[0]), e);
				throw new RemoteOpenmrsSearchException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			}
			// TODO: Currently location tags and attributes are not being used so we can ignore them. however a complete solution will have to take
			// these into account.
			List<String> ignoredFields = Arrays.asList("display", "tags", "parentLocation", "childLocations", "attributes",
			    "links", "resourceVersion", "auditInfo");
			final Set<String> allFields = fetchedLocationObject.keySet();
			allFields.removeAll(ignoredFields);
			
			final Location fetchedLocation = new Location();
			ReflectionUtils.doWithFields(Location.class, new ReflectionUtils.FieldCallback() {
				@Override
				public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
					field.setAccessible(true);
					field.set(fetchedLocation, fetchedLocationObject.get(field.getName()));
					field.setAccessible(false);
				}
			}, new ReflectionUtils.FieldFilter() {
				@Override
				public boolean matches(Field field) {
					return allFields.contains(field.getName());
				}
			});

			if(fetchedLocationObject.containsKey("")) {
				updateAuditInfo(fetchedLocation, (Map) fetchedLocationObject.get("auditInfo"));
			}

			if (fetchedLocationObject.containsKey("parentLocation") && fetchedLocationObject.get("parentLocation") != null) {
				// Check if this location exists locally
				String parentLocationUuid = (String) ((Map) fetchedLocationObject.get("parentLocation")).get("uuid");
				Location parentLocation = locationService.getLocationByUuid(parentLocationUuid);
				
				if (parentLocation == null) {
					parentLocation = importLocationFromRemoteOpenmrsServer(parentLocationUuid);
				}
				fetchedLocation.setParentLocation(parentLocation);
			}
			return locationService.saveLocation(fetchedLocation);
		}
		throw new RemoteOpenmrsSearchException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	}
	
	public String[] getRemoteOpenmrsHostUsernamePassword() throws RemoteOpenmrsSearchException {
		String message = "Could not fetch data, Global property %s not set";
		String remoteServerUrl = adminService.getGlobalProperty(OPENMRS_REMOTE_SERVER_URL_GP);
		String remoteServerUsername = adminService.getGlobalProperty(OPENMRS_REMOTE_SERVER_USERNAME_GP);
		String remoteServerPassword = adminService.getGlobalProperty(OPENMRS_REMOTE_SERVER_PASSWORD_GP);
		if (StringUtils.isEmpty(remoteServerUrl)) {
			LOGGER.warn(String.format(message, OPENMRS_REMOTE_SERVER_URL_GP));
			throw new RemoteOpenmrsSearchException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		if (StringUtils.isEmpty(remoteServerUsername)) {
			LOGGER.warn(String.format(message, OPENMRS_REMOTE_SERVER_USERNAME_GP));
			throw new RemoteOpenmrsSearchException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		if (StringUtils.isEmpty(remoteServerPassword)) {
			LOGGER.warn(String.format(message, OPENMRS_REMOTE_SERVER_PASSWORD_GP));
			throw new RemoteOpenmrsSearchException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
		
		return new String[] { remoteServerUrl, remoteServerUsername, remoteServerPassword };
	}
}
