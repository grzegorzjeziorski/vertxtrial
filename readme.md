# Simple transaction service

## Purpose
Service supports basic operations simulating bank accounts.
The following operations are supported:
* Account creation
* Accounts listing
* Deposing money
* Withdrawing money
* Transferring money between accounts
* Listing transactions for given account also for specified time range

## Required dependencies
* maven (tested with version 3.6.3)
* jdk (tested with version 1.8.0_201)

## Running Service
In order to run tests and package project 
```
mvn clean install
```
In order to run service
```
java -jar target/vertx-trial-0.0.1-fat.jar  
```

## Examples of service invocation
Create account 
```
curl -i -X POST -H 'Content-Type: application/json' -d '{"name": "John", "surname": "Doe"}' http://localhost:8080/api/accounts
```

List accounts
```
curl -i -X GET http://localhost:8080/api/accounts
```

Deposing money
```
curl -i -X POST -H 'Content-Type: application/json' -d '{"destination_account_id": 0, "transaction_type": "DEPOSIT", "amount": 50.0}' http://localhost:8080/api/transactions
```

Withdrawing money
```
curl -i -X POST -H 'Content-Type: application/json' -d '{"destination_account_id": 0, "transaction_type": "WITHDRAW", "amount": 120.0}' http://localhost:8080/api/transactions
```

Transferring money
```
curl -i -X POST -H 'Content-Type: application/json' -d '{"source_account_id": 0, "destination_account_id": 1, "transaction_type": "TRANSFER", "amount": 120.0}' http://localhost:8080/api/transactions
```

Listing transactions
```
curl -i -X GET http://localhost:8080/api/transactions?account-id=0
```

Exact specification can be found in api.yaml

## Testing
Additionally service was tested by generating multiple concurrent request using [siege](https://github.com/JoeDog/siege). 
Examples of siege commands can be found in ```load-test/siege```