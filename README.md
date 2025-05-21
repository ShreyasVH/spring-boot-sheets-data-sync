Tools used:
 - Java 17.0.7
 - Maven 3.8.8
 - Postgres 16.4

##Overall flow:
1. A recurring job `sync-job` is scheduled to run every hour to ensure data sync between the sheet and postgres DB
2. A recurring job `alert-job` is scheduled to run every day at 11 30 PM to send an alert for list of hospitals exceeding the occupancy limit on either bed type.


##Steps to Run the application:
1. Create a DB in postgres
2. Set the following env variables:
   - POSTGRES_IP - IP of the postgres instance
   - POSTGRES_PORT - port at which postgres is running
   - POSTGRES_DB - name of the database to use
   - POSTGRES_USER - user to access the potgres database
   - POSTGRES_PASSWORD - password of the user
   - JOB_RUNNER_PORT - port on which the Job runner dashboard UI can be accessed
   - ALERT_EMAIL_TO - the receipient email address for the lert email
   - ALERT_EMAIL_FROM - from address for the alert email
   - MAIL_APP_PASSWORD - App password for sending email
   - GOOGLE_SHEET_ID - identifier of the google sheet to sync data 
   - PORT - Port to use for the application 
   - DEBUG_PORT - Port on which remote debugger can run
3. Copy the service-account.json file to the path `src/main/resources`
4. The application can be started using:
   
    ```bash start.sh```
5. The scheduled jobs can be viewed via the JobRunr Dashboard UI