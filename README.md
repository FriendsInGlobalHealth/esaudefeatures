eSaude Features Module
============================

Description
-----------
This module houses eSaude general custom features that do not warrant a need for a new independent module by themselves.

Features
--------
1. Cohort Based Programs.
2. Remote Patient Search & Importing.

## 1. Cohort Based Programs
This feature enhances the program management by allowing association of a program with cohorts. Consequently a patient can be enrolled in a program only if s/he belongs to any of cohorts linked with the program. The module modifies the program creation/editing legacy UI and also the behaviour of the program enrollment on the patient dashboard.

### Implementation
The cohort based program features is achieved by introducing a new table named `esaudefeatures_program_cohort` which links program with its associated cohorts. The module works by redirecting the program creation/editing page to a custom version which includes the ability to associate a program with cohorts. See the difference between the pages displayed with & without the module running.

**Note:** The cohort should already be configured. The cohorts can be created using the *Reporting compatibility module*

### Program Creation/Editing page while the module is not running

![Program page while module is not running](images/program_page_module_not_running.png)

### Program Creation/Editing page while the module is running.

![Program page while module is running](images/cohortprograms.edit.program.png)

### Program enrollment for a patient belonging to cohort associated with the program.
The program *Stay Fit* is associated with a cohort of young people of 14-21 years of age, while the program *Aproveite a vida* is free for any patient to enroll.

![Program enrollment for patient in cohort](images/patient.in.cohort.png)

### Program enrollment for a patient not belonging to cohorts associated with *Stay Fit* program

![Program enrollment for patient in cohort](images/patient.not.in.cohort.png)

## 2. Remote Patient Search & Importing.
This feature enables the user to configure a remote server. The remote server can either be of type OpenMRS or an [OpenCR](https://intrahealth.github.io/client-registry/) 
instance. The remote server type is configured as a global property along with others. When adding a new patient, the user can search and confirm 
that the patient has not been created in another facility in the same catchment area. This is done by searching using either name or an identifier 
provided by the patient. If the user is satisfied that the records matches the patient details exactly then they can simply import the patient 
demographic information from the source instead of recreating the same information.

### Benefits
1. Reduces duplicates in a particular catchment area since an imported patient will be recognized as already existing once the data is synchronized back to the central server.
2. Reduces human error since the information is automatically imported without any changes though the user can edit the imported record should they wish to do so.

### Assumptions When The Remote Server is an OpenMRS instance
Both the client and central server share the same metadata (that is things like patient identifier types,
relationship types, name validation logic, person attribute types).

### Assumptions When The Remote Server is an OpenCR instance
All identifier types from OpenCR are assumed to already be present in an OpenMRS instance performing a search.

### Implementation
For an OpenMRS remote server type the feature is implemented purely on the browser by performing direct rest calls for the fetch and data import.

However, for an OpenCR remote server type, the feature is implemented by sending the request through the OpenMRS instance performing the search. The decision to do it
this way instead of firing the REST calls directly from the browser is because of the browser restrictions on cross origin requests which are not 
accommodated when using [JSON Web Token (JWT)](https://en.wikipedia.org/wiki/JSON_Web_Token) authentication method. The OpenCR implementation does not support cross 
origin headers when using JWT(as of this documentation), but, it does support it when using client certificate authentication. The later was considered, 
however it became apparent that using client TLS certificates will practically be impossible because it will have to be configured in every browser whose 
OpenMRS user wishes to search against the OpenCR instance.

### Requirements & Configuration
Both the client and server needs to be configured.

#### Client Machine Configuration
1. Client machine is configured with the following parameters as global properties. (See the following screenshot for clarity)
    1. `Opencr Identifier Type Concept Mappings` which mirrors the corresponding MPI (OpenCR) property defined by EIP module. The property tells the module the OpenMR UUIDs 
    corresponding to the identify types stored in MPI server.
    2. `Opencr Patient Uuid Concept Map` which is used to identify the code used for OpenMRS person/patient UUID in MPI.
    3. `Opencr Remote Server Password` which is the password for the OpenCR MPI server username.
    4. `Opencr Remote Server Url` which is the URL of the OpenCR MPI server from which to search for patients.
    5. `Opencr Remote Server Username` which is the user who has privilege to view patients on the OpenCR MPI server.
    6. `Openmrs Remote Server Password` which is the password for the remote OpenMRS server username.
    7. `Openmrs Remote Server Url` which is the URL of the OpenMRS server from which to search for patients
    8. `Openmrs Remote Server Username` which is the Username of the user who has privilege to view patients on the OpenMRS remote server.
    9. `Remote Server Skip Hostname Verification` which is the property tells the system whether to skip hostname verification during the SSL handshake.
    It can be for self signed SSL certificates, otherwise this should never be set to true. Set to TRUE if you know what you are doing.
    10. `Remote server type` which specifies whether the remote server is of type OPENMRS or OPENCR.
![Remote server configuration parameters as global properties](images/global_properties.png)

2. Client machine needs to be running rest webservices module version 2.29+
3. When searching against an OpenCR server with a self signed certificate, the client java running the OpenMRS instance should be configured to trust the self signed
certificates. Without this the search is not possible throwing a security exception. This is done by getting the copy of the certificate and importing it into 
the corresponding java certificate store using the following command (**Note:** Ensure the JAVA_HOME environmental variable is set).
```
$ sudo keytool -importcert -file <path-to-self-signed-certificate>/server_cert.pem -keystore $JAVA_HOME/jre/lib/security/cacerts -alias opencrselfsigned
```


#### OpenMRS Remote/Central Server Configuration
1. Server need to be running rest webservices module version 2.29+

### Using Remote Patient Search
1. To access it use the menu "Remote Patients" On the Gutter as shown below.

![Remote Patients gutter menu](images/remote_patients_gutter_menu.png)

Building from Source
--------------------
You will need to have Java 1.7+ and Maven 3.x+ installed.  Use the command 'mvn package' to 
compile and package the module.  The .omod file will be in the omod/target folder.

Alternatively you can add the snippet provided in the [Creating Modules](https://wiki.openmrs.org/x/cAEr) page to your 
omod/pom.xml and use the mvn command:

    mvn package -P deploy-web -D deploy.path="../../openmrs-1.11.x/webapp/src/main/webapp"

It will allow you to deploy any changes to your web 
resources such as jsp or js files without re-installing the module. The deploy path says 
where OpenMRS is deployed.

Installation
------------
1. Build the module to produce the .omod file.
2. Use the OpenMRS Administration > Manage Modules screen to upload and install the .omod file.

If uploads are not allowed from the web (changable via a runtime property), you can drop the omod
into the ~/.OpenMRS/modules folder.  (Where ~/.OpenMRS is assumed to be the Application 
Data Directory that the running openmrs is currently using.)  After putting the file in there 
simply restart OpenMRS/tomcat and the module will be loaded and started.
