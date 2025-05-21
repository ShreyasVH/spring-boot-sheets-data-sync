package com.example.sheets.services.impl;

import com.example.sheets.dtos.BedTypeCount;
import com.example.sheets.models.db.Hospital;
import com.example.sheets.models.db.Patient;
import com.example.sheets.repositories.HospitalRepository;
import com.example.sheets.repositories.PatientRepository;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.spring.annotations.Recurring;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
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

    @Autowired
    private EmailService emailService;

    private final int batchSize = 50;

    private final double occupancyThreshold = 0.9;

    @Value("${google.sheets.id}")
    private String sheetId;

    @Value("${alert.email.to}")
    private String alertEmailAddress;

    @Recurring(id = "sync-job", cron = "0 */1 * * *")
    @Job(name = "Sync job")
    public void syncJob(JobContext jobContext) throws IOException, ParseException {
        jobContext.logger().info("started job");

        jobContext.logger().info("syncing hospitals");
        syncHospitals();
        jobContext.logger().info("syncing patients");
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
    @Job(name = "Alert job")
    public void alertJob(JobContext jobContext) throws MessagingException {
        jobContext.logger().info("started job");

        List<EmailTableRow> eligibleHospitals = getRowsForEmailTable();
        jobContext.logger().info("Number of eligible hospitals: " + eligibleHospitals.size());
        if(!eligibleHospitals.isEmpty())
        {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            String subject = String.format("Daily Occupancy Alert: Hospitals Exceeding 90%% Capacity – %s", dateFormat.format(new Date()));

            String content = getEmailContent(eligibleHospitals);

            emailService.sendEmail(alertEmailAddress, subject, content);
        }
    }

    @Data
    @AllArgsConstructor
    class EmailTableRow
    {
        private String id;
        private String name;
        private int generalBedCount;
        private long generalBedOccupiedCount;
        private int icuBedCount;
        private long icuBedOccupiedCount;
    }

    private List<EmailTableRow> getRowsForEmailTable()
    {
        long hospitalCount = hospitalRepository.count();
        long totalPages = (hospitalCount + batchSize - 1) / batchSize;

        List<EmailTableRow> eligibleHospitals = new ArrayList<>();

        int page = 0;
        while(page < totalPages)
        {
            Pageable pageable = PageRequest.of(page, batchSize, Sort.by("id").ascending());
            Page<Hospital> hospitalPage = hospitalRepository.findAll(pageable);
            List<Hospital> hospitals = hospitalPage.getContent();

            for(Hospital hospital: hospitals)
            {
                List<BedTypeCount> bedTypeCounts = patientRepository.countBedTypesByHospitalId(hospital.getId());

                Map<String, Long> bedTypeCountMap = bedTypeCounts.stream().collect(Collectors.toMap(BedTypeCount::getBedType, BedTypeCount::getCount));

                long generalBedsOccupied = bedTypeCountMap.getOrDefault("General", 0L);
                long icuBedsOccupied = bedTypeCountMap.getOrDefault("ICU", 0L);

                if(
                    generalBedsOccupied * 1.0 / hospital.getGeneralBedCount() > occupancyThreshold
                            ||
                    icuBedsOccupied * 1.0 / hospital.getIcuBedCount() > occupancyThreshold
                )
                {
                    EmailTableRow emailTableRow = new EmailTableRow(hospital.getId(), hospital.getName(), hospital.getGeneralBedCount(), generalBedsOccupied, hospital.getIcuBedCount(), icuBedsOccupied);
                    eligibleHospitals.add(emailTableRow);
                }
            }

            page++;
        }

        return eligibleHospitals;
    }

    private String getEmailContent(List<EmailTableRow> eligibleHospitals)
    {
        StringBuilder content = new StringBuilder();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        content.append(String.format("<p>Please find below the list of hospitals whose occupancy exceeded 90%% as of %s:</p>", dateFormat.format(new Date())));

        content.append("<table style=\"border-collapse: collapse\">");
        content.append("<thead>");
        content.append("<tr>");
        content.append("<th style=\"border: 1px solid black; padding: 8px\">Id</th>");
        content.append("<th style=\"border: 1px solid black; padding: 8px\">Name</th>");
        content.append("<th style=\"border: 1px solid black; padding: 8px\">Total General Beds</th>");
        content.append("<th style=\"border: 1px solid black; padding: 8px\">General Beds Occupied</th>");
        content.append("<th style=\"border: 1px solid black; padding: 8px\">Total ICU Beds</th>");
        content.append("<th style=\"border: 1px solid black; padding: 8px\">ICU Beds Occupied</th>");
        content.append("</tr>");
        content.append("</thead>");

        content.append("<tbody>");

        for(EmailTableRow row: eligibleHospitals)
        {
            content.append("<tr>");

            content.append(String.format("<td style=\"border: 1px solid black; padding: 8px\">%s</td>", row.getId()));
            content.append(String.format("<td style=\"border: 1px solid black; padding: 8px\">%s</td>", row.getName()));
            content.append(String.format("<td style=\"border: 1px solid black; padding: 8px\">%d</td>", row.getGeneralBedCount()));
            content.append(String.format("<td style=\"border: 1px solid black; padding: 8px\">%d</td>", row.getGeneralBedOccupiedCount()));
            content.append(String.format("<td style=\"border: 1px solid black; padding: 8px\">%d</td>", row.getIcuBedCount()));
            content.append(String.format("<td style=\"border: 1px solid black; padding: 8px\">%d</td>", row.getIcuBedOccupiedCount()));

            content.append("</tr>");
        }

        content.append("</tbody>");
        content.append("</table>");

        return content.toString();
    }
}