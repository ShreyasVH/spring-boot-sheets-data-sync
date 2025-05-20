package com.example.sheets.services.impl;

import com.example.sheets.models.db.Hospital;
import com.example.sheets.models.db.Patient;
import com.example.sheets.repositories.HospitalRepository;
import com.example.sheets.repositories.PatientRepository;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.spring.annotations.Recurring;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JobService {

    @Autowired
    private Sheets sheetsService;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private PatientRepository patientRepository;

    private final int batchSize = 2;

    @Value("${google.sheets.id}")
    private String sheetId;

    @Recurring(id = "sync-job", cron = "0 */1 * * *")
    @Job(name = "Sync job")
    public void syncJob(JobContext jobContext) throws IOException, ParseException {
        jobContext.logger().info("started job");

        syncHospitals();
        syncPatients();
    }

    private Set<String> getIds(String sheetName, char column) throws IOException
    {
        ValueRange hospitalIdResponse = sheetsService.spreadsheets().values()
                .get(sheetId, String.format("%s!%c:%c", sheetName, column, column))
                .execute();

        List<List<Object>> idList = hospitalIdResponse.getValues();

        Set<String> ids = new HashSet<>();
        int index = 0;
        for(List<Object> row: idList)
        {
            if(index > 0)
            {
                String id = row.get(0).toString();
                ids.add(id);
            }

            index++;
        }

        return ids;
    }

    private void syncHospitals() throws IOException
    {
        int startingRow = 2;
        char startingColumn = 'A';
        char endingColumn = 'E';
        String sheetName = "Hospital";

        Set<String> hospitalIds = getIds(sheetName, startingColumn);
        int totalHospitals = hospitalIds.size();

        while(startingRow <= totalHospitals)
        {
            int endingRow = startingRow + batchSize - 1;
            List<Hospital> hospitalsToUpdate = new ArrayList<>();
            List<Hospital> hospitalsToAdd = new ArrayList<>();
            ValueRange hospitalResponse = sheetsService.spreadsheets().values()
                    .get(sheetId, String.format("%s!%c%d:%c%d", sheetName, startingColumn, startingRow, endingColumn, endingRow))
                    .execute();

            List<List<Object>> valueList = hospitalResponse.getValues();
            List<String> batchHospitalIds = valueList.stream().map(row -> row.get(0).toString()).collect(Collectors.toList());

            List<Hospital> existingHospitals = hospitalRepository.findAllById(batchHospitalIds);
            Map<String, Hospital> existingHospitalMap = existingHospitals.stream().collect(Collectors.toMap(Hospital::getId, hospital -> hospital));
            Set<String> existingHospitalIds = existingHospitals.stream().map(Hospital::getId).collect(Collectors.toSet());

            for(List<Object> row: valueList)
            {
                String id = row.get(0).toString();
                String name = row.get(1).toString();
                String location = row.get(2).toString();
                int generalBedCount = Integer.parseInt(row.get(3).toString());
                int icuBedCount = Integer.parseInt(row.get(4).toString());

                if(existingHospitalIds.contains(id))
                {
                    Hospital hospital = existingHospitalMap.get(id);

                    boolean updateRequired = false;

                    if(!name.equals(hospital.getName()))
                    {
                        updateRequired = true;
                        hospital.setName(name);
                    }

                    if(!location.equals(hospital.getLocation()))
                    {
                        updateRequired = true;
                        hospital.setLocation(location);
                    }

                    if(generalBedCount != hospital.getGeneralBedCount())
                    {
                        updateRequired = true;
                        hospital.setGeneralBedCount(generalBedCount);
                    }

                    if(icuBedCount != hospital.getIcuBedCount())
                    {
                        updateRequired = true;
                        hospital.setIcuBedCount(icuBedCount);
                    }

                    if(updateRequired)
                    {
                        hospitalsToUpdate.add(hospital);
                    }
                }
                else
                {
                    Hospital hospital = new Hospital(id, name, location, generalBedCount, icuBedCount);
                    hospitalsToAdd.add(hospital);
                }
            }

            if(!hospitalsToUpdate.isEmpty())
            {
                hospitalRepository.saveAll(hospitalsToUpdate);
            }

            if(!hospitalsToAdd.isEmpty())
            {
                hospitalRepository.saveAll(hospitalsToAdd);
            }
            startingRow += batchSize;
        }

        String lastId = null;
        while (true) {
            Pageable pageable = PageRequest.of(0, batchSize, Sort.by("id").ascending());
            List<Hospital> hospitals = hospitalRepository.findNextBatch(lastId, pageable);

            if (hospitals.isEmpty()) {
                break;
            }

            List<Hospital> hospitalsToDelete = new ArrayList<>();
            for (Hospital hospital : hospitals) {
                if (!hospitalIds.contains(hospital.getId())) {
                    hospitalsToDelete.add(hospital);
                }
            }

            if (!hospitalsToDelete.isEmpty()) {
                hospitalRepository.deleteAll(hospitalsToDelete);
            }

            lastId = hospitals.get(hospitals.size() - 1).getId();
        }
    }

    private void syncPatients() throws IOException, ParseException
    {
        int startingRow = 2;
        char startingColumn = 'A';
        char endingColumn = 'F';
        String sheetName = "Patient";

        Set<String> patientIds = getIds(sheetName, startingColumn);
        int totalPatients = patientIds.size();

        while(startingRow <= (totalPatients + 1))
        {
            int endingRow = startingRow + batchSize - 1;
            List<Patient> patientsToUpdate = new ArrayList<>();
            List<Patient> patientsToAdd = new ArrayList<>();
            ValueRange patientResponse = sheetsService.spreadsheets().values()
                    .get(sheetId, String.format("%s!%c%d:%c%d", sheetName, startingColumn, startingRow, endingColumn, endingRow))
                    .execute();

            List<List<Object>> valueList = patientResponse.getValues();
            List<String> batchPatientIds = valueList.stream()
                    .map(row -> row.get(0).toString())
                    .collect(Collectors.toList());

            List<Patient> existingPatients = patientRepository.findAllById(batchPatientIds);
            Map<String, Patient> existingPatientMap = existingPatients.stream()
                    .collect(Collectors.toMap(Patient::getId, patient -> patient));
            Set<String> existingPatientIds = existingPatients.stream()
                    .map(Patient::getId)
                    .collect(Collectors.toSet());

            List<String> hospitalNames = valueList.stream().map(row -> row.get(5).toString()).distinct().collect(Collectors.toList());
            List<Hospital> hospitals = hospitalRepository.findAllByNameIn(hospitalNames);
            Map<String, String> hospitalMap = hospitals.stream().collect(Collectors.toMap(Hospital::getName, Hospital::getId));

            for(List<Object> row: valueList)
            {
                String id = row.get(0).toString();
                String name = row.get(1).toString();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                Date dateOfBirth = dateFormat.parse(row.get(2).toString());
                String disease = row.get(3).toString();
                String bedType = row.get(4).toString();
                String hospitalName = row.get(5).toString();
                String hospitalId = hospitalMap.get(hospitalName);

                if(existingPatientIds.contains(id))
                {
                    Patient patient = existingPatientMap.get(id);

                    boolean updateRequired = false;

                    if(!name.equals(patient.getName()))
                    {
                        updateRequired = true;
                        patient.setName(name);
                    }

                    if(dateOfBirth.getTime() != patient.getDateOfBirth().getTime())
                    {
                        updateRequired = true;
                        patient.setDateOfBirth(new java.sql.Date(dateOfBirth.getTime()));
                    }

                    if(!disease.equals(patient.getDisease()))
                    {
                        updateRequired = true;
                        patient.setDisease(disease);
                    }

                    if(!bedType.equals(patient.getBedType()))
                    {
                        updateRequired = true;
                        patient.setBedType(bedType);
                    }

                    if(!hospitalId.equals(patient.getHospitalId()))
                    {
                        updateRequired = true;
                        patient.setHospitalId(hospitalId);
                    }

                    if(updateRequired)
                    {
                        patientsToUpdate.add(patient);
                    }
                }
                else
                {
                    Patient patient = new Patient(id, name, new java.sql.Date(dateOfBirth.getTime()), disease, bedType, hospitalId);
                    patientsToAdd.add(patient);
                }
            }

            if(!patientsToUpdate.isEmpty())
            {
                patientRepository.saveAll(patientsToUpdate);
            }

            if(!patientsToAdd.isEmpty())
            {
                patientRepository.saveAll(patientsToAdd);
            }
            startingRow += batchSize;
        }

        String lastId = null;
        while (true) {
            Pageable pageable = PageRequest.of(0, batchSize, Sort.by("id").ascending());
            List<Patient> patients = patientRepository.findNextBatch(lastId, pageable);

            if (patients.isEmpty()) {
                break;
            }

            List<Patient> patientsToDelete = new ArrayList<>();
            for (Patient patient : patients) {
                if (!patientIds.contains(patient.getId())) {
                    patientsToDelete.add(patient);
                }
            }

            if (!patientsToDelete.isEmpty()) {
                patientRepository.deleteAll(patientsToDelete);
            }

            lastId = patients.get(patients.size() - 1).getId();
        }
    }

    @Recurring(id = "alert-job", cron = "30 23 * * *")
    @Job(name = "Daily job")
    public void dailyJob(JobContext jobContext) throws IOException {
        jobContext.logger().info("started job");

        String sheetId = "1im3kg31RL7FDNL_V_R7tSwqNq66vgKgpvzrn0ADGzZs";

        ValueRange response = sheetsService.spreadsheets().values()
                .get(sheetId, "Hospital!A2:E9")
                .execute();

//        jobContext.logger().info(response.getValues());
        String sh = "sh";
    }
}