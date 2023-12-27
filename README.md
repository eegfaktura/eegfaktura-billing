![eegfaktura Logo](https://github.com/eegfaktura/eegfaktura-billing/blob/master/eegfaktura-logo.PNG)

# eegfaktura-billingj
> Do the actual billing

This service is part of the eegfaktura software. This software allows renewable energy communities (EEGs)
to do the billing. 

To setup full eegfaktura software see our eegfaktura-docker-compose project.

## Installing / Getting started

### Prerequisities
In order to run this container you'll need docker installed.

* Windows
* macOS
* Linux

## Built with

* Spring Boot 3.1.1
* JDK 17
* Flyway 9.16
* JasperReports 6.20.4
* OpenAPI 2.1.0

## Developing

```shell
git clone https://github.com/eegfaktura/eegfaktura-billing.git
mvn clean package
```

### Building

Usually this project can be run and executed in your IDE right away without
much configuration. If you need to build the service outside your IDE:

```shell
mvn clean package
```

### Deploying / Publishing

You can deploy the container with docker or docker-compose as you wish.

## Rest API
You can inspect and play with this services' API with the help of swagger (only visible when services is started with 'dev' profile!):
http://youserver:8080/swagger-ui/index.html

## Features

This (tenant-aware) service is responsible to do the actual billing. It provides:
* billing configuration features
* billing features
* rendering billing documents (invoices, credit notes, billing information documents)
* sending billing documents to the recipients via mail
* exporting billing information (xls, zip)

## Configuration

The services configuration is located in /src/main/resources/application.properties
The container is accepting the following environment variables as configuration:

* `JDBC_DATABASE_URL` database URL
* `JDBC_DATABASE_USERNAME` database user
* `JDBC_DATABASE_PASSWORD` database password
* `MAIL_HOST` hostname of mailserver
* `MAIL_PORT` port of mailserver on host
* `MAIL_USER` user to access mailserver
* `MAIL_PASSWORD` password to access mailserver
* `MAIL_SMTP_AUTH` (default = true)
* `MAIL_SMTP_STARTTLS_ENABLE` (default = false)
* `MAIL_NO_REPLY_TO` e-mail address to use as sender for automated mails (default = no-reply@eegfaktura.at)
* `JWT_PUBLICKEYFILE` full path to the JWT public key file, used to decrypt JWT tokens from header (default = /billing/zertifikat-pub.pem)

## Contributing

When you publish something open source, one of the greatest motivations is that
anyone can just jump in and start contributing to your project.

These paragraphs are meant to welcome those kind souls to feel that they are
needed. You should state something like:

"If you'd like to contribute, please fork the repository and use a feature
branch. Pull requests are warmly welcome."

## Links

- Project homepage: https://github.com/eegfaktura
- Repositories
  -- Docker: https://github.com/eegfaktura/eegfaktura-billing-docker
  -- Sources: https://github.com/eegfaktura/eegfaktura-billing/
- In case of sensitive bugs like security vulnerabilities, please contact
    peterO@vfeeg.org directly instead of using issue tracker. We value your effort
    to improve the security and privacy of this project!

## Licensing

See LICENSE.md
