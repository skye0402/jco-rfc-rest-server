# REST2RFC Proxy
## Let your RFC function modules meet the web!
### Built with Eclipse using SAP Cloud SDK for Java and to be deployed on SAP BTP
A JCo-based REST to RFC server using BTP destinations. It is a Java based SAP project that allows you to use RFC function modules in web services by invoking a function module in an ABAP system via RFC through BTP RFC destination service.

Check out the 2 blogs that describe the use case and functions in detail:
* [SAP Build Apps and BAPIs: RFC meets Web](https://blogs.sap.com/2023/04/02/sap-build-apps-and-bapis-rfc-meets-web/)
* [REST to RFC proxy – Development on BTP (Part II)](https://blogs.sap.com/2023/04/04/rfc-meets-the-web-building-the-proxy/)

## Features

- Exposes SAP function modules as RESTful API endpoints.
- Handles HTTP GET and POST requests.
- Handles SAP role-based authorization for Display and Modify roles.
- Supports input and output parameters for function modules.
- Supports transactional execution of function modules.
- Handles exceptions and provides informative error messages.

## Installation

1. Clone the repository to your local machine.
2. Open the project in your favorite Java IDE.
3. Import the required dependencies (See `pom.xml` for the complete list).
4. Adjust the `btpDestination` variable to point to your SAP system.

## Usage

- HTTP GET: Used for retrieving data from a function module.
  - Example: `/rfc?fm=FUNCTION_MODULE_NAME&query=QUERY_PARAMETERS`
- HTTP POST: Used for sending data to a function module.
  - Example: `/rfc?fm=FUNCTION_MODULE_NAME`
  - Body: JSON data

## Exception Handling

Exceptions are returned as a JSON response with the `exception` and `errormessage` fields. For example:

```json
{
  "exception": "JCo exception occurred while executing FM in destination DEST.",
  "errormessage": "Detailed error message"
}
```
## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
