package uk.nhs.nhsdigital.fhirvalidator.util;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;

import java.math.BigInteger;
import java.util.UUID;

public final class FhirSystems {
    public static final String EMIS_PRACTITIONER =  "https://emis.com/Id/Practitioner/GUID";

    public static final String EXTENSION_LOCATION_TYPE = "http://fhir.virtuallyhealthcare.co.uk/LocationType";

    public static final String EXTENSION_LOCATION = "http://fhir.virtuallyhealthcare.co.uk/Location";

    public static final String NHS_GMP_NUMBER = "https://fhir.hl7.org.uk/Id/gmp-number";

    public static final String NHS_GMC_NUMBER = "https://fhir.hl7.org.uk/Id/gmc-number";

    public static final String NHS_NUMBER = "https://fhir.nhs.uk/Id/nhs-number";

    public static final String ODS_CODE = "https://fhir.nhs.uk/Id/ods-organization-code";

    public static final String VIRTUALLY_CONNECTION_TYPE = "http://fhir.virtuallyhealthcare.co.uk/ConnectionType";

    public static final String AWS_LOCATION_IDENTIFIER = "https://fhir.virtually.healthcare/Id/Location";

    public static final String AWS_TASK_IDENTIFIER = "https://fhir.virtually.healthcare/Id/Task";

    public static final String EMIS_PATIENT_IDENTIFIER = "https://emis.com/Id/Patient/DBID";
    public static final String EMIS_PATIENT_ODS_IDENTIFIER = "https://emis.com/Id/Patient/ID";

    public static final String EMIS_PRACTITIONER_IDENTIFIER = "https://emis.com/Id/Practitioner/DBID";

    public static final String SNOMED_CT = "http://snomed.info/sct";

    public static final String DMandD= "https://dmd.nhs.uk";

    public static final String ISO_EHR_EVENTS = "http://terminology.hl7.org/CodeSystem/iso-21089-lifecycle";

    public static final String FHIR_RESOURCE_TYPE = "http://hl7.org/fhir/resource-types";

    public static final String DICOM_AUDIT_ROLES = "http://dicom.nema.org/resources/ontology/DCM";

    public static final String V3_ROLE_CLASS = "http://terminology.hl7.org/CodeSystem/v3-RoleClass";

    public static final String V3_PARTICIPANT_TYPE = "http://terminology.hl7.org/CodeSystem/v3-ParticipationType";

    public static final String stripBrace(String str) {
        return str.replace("{","").replace("}","");
    }

    public static final String cachedQuery(String host, String path, String query) {
        if (path == null) path = "root";
        if (query== null) query = "";
        return host + "/" + path + "?" + query;}




    public static String getId(String reference) {
        String[] strings = reference.split("/");
        return strings[strings.length - 1];
    }

    public static boolean isNumeric(String reference) {
        String id = getId(reference);
        try {
            BigInteger.valueOf(Long.valueOf(id));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
    public static boolean isUUID(String reference) {
        String id = getId(reference);
        try {
            UUID uuid = UUID.fromString(id);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }



}
