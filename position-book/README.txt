BUSINESS OVERVIEW:
    Create a Position Book that stores the total quantity of traded securities in real time.This position book maintains the following detiails 
        1. Trading account Number
        2. Security Identifier
        3. Quantity of the security identifier
        4. Details of all the events like BUY, SELL or  CANCEL

BUSINESS REQUIREMENT:
    1. Build a REST endpoint to create an BUY, SELL or CANCEL event
    2. Build an endpoint to retrieve the events stored in the position book across  the trading account and security identifier

ASSUMPTIONS:
    1. Any trade processing event always comes with an unique identifier
    2. If two events arrive with the same id and account number, the second event will be rejected as a duplicate regardless of the security identifier or quantity.
    3. A cancellation event must carry an id that matches an existing BUY or SELL event for the same account number. If no such event exists, the cancellation is invalid. The quantity and security identifier in a CANCEL event are meaningless and are not used to process the cancellation.
    4. Event Type should be only BUY, SELL or CANCEL any other type will be considered invalid 
    5. Security, authentication and authorisation are taken care by using an API GATEWAY

ACCEPTANCE CRITERIA:

1. When a BUY event is processed using post /v1/position-book/process endpoint the position book should be updated with id , event type, trading account number , security identifier and quantity if the position book for that account number and id are empty and give 201 created
2. When a BUY event of the same trading account number and id comes with a different security identifier or quantity or same security identifier and same quantity(duplicate) should give 409 already exists
3. If no id or no account number or no security identifier or no quantity is passed in the input the api should return 400 bad request
4. If any other event type other than BUY, SELL or CANCEL is given the api should return 400 bad request
5. If a CANCEL event comes with an id and account number for which no prior BUY or SELL event exists, return 400 bad request.
6. If a CANCEL event comes with an id and account number for which a prior BUY or SELL event exists, the cancellation event should be captured in the event history and the position for that account number and security identifier should reflect the reversal — the cancelled quantity should no longer be part of the active position.
7. If a SELL event occurs and there is no security identifier for that account number or id then return 404 data not found
8.If a SELL event occurs and there is no enough quantity for the security identifier for that account number or id then return 400 bad request with message saying insufficient securities to sell

ARCHITECTURAL DECISIONS RECORDS

    1. Use ConcurrentHadhMap data structure as this is a real time api and there is a possibility of concurrent reads and writes at the same .
    2. Use atomic integer for quantity cause concurrentHashMap will not avoid race condition 
    3. Use One Map for eventstore to store the events and one for postions to store the positions in order to seperate command and query respobnsibilities 
    4. use only account number in get endpoint cause consumers would defnetly want to search using minimal input parameters but this would require to scan full position store scan 

ENDPOINTS:
    POST : http://localhost:8080/v1/position-book/process/{id}?eventType={BUY/SELL/CANCEL}
    Request Body:
    {
    "accountNumber": "ACC1",
    "securityIdentifier": "SEC1",
    "quantity": 100
    }
    GET: http://localhost:8080/v1/position-book/{tradingAccountNumber}\
    Response Body:
    [
    {
        "accountNumber": "ACC1",
        "securityIdentifier": "SEC1",
        "netQuantity": 100,
        "eventHistory": [
            {
                "id": 1,
                "eventType": "BUY",
                "quantity": 100
            }
        ]
    }
]
SWAGGER ENDPOINT : http://localhost:8080/swagger-ui/index.html
ACUTATOR ENDPOINT : http://localhost:8080/actuator/health
