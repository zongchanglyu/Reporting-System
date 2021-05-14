# Make improvement in the code/system level.

1. Set up AWS environment.
2. Add new features: update and delete.(Not just update/delete locally on Client server but also take care of PDFService and ExcelService)
3. Improve sync API performance by using multithreading and sending request concurrently to both services.
4. Fix bugs and complete some functionalities(i.e. the original code did not upload new generated excel files to S3)
5. Handle more exceptions
6. Use a database instead of hashmap in the ExcelRepositoryImpl.

#### To be continued:
#### Improve code coverage by adding more tests.
#### Convert sync API into microservices by adding Eureka/Ribbon support.
#### Add pressure tests to benchmark the system. (Using Jmeter)
...
