### Basics
Routes file must be named exactly "endpoints" and must be available in a classpath at "/" location.

One endpoint defined by one line of three parts:
`Method [1+ space] route path [1+ space] handler`  
Simple example:  
`GET    /api/version    controllers.MyController.getVersion`

Method is just a name of the http method.

Endpont path is an absolute path, may contain named variables (see examples below)

Handler is a some controller's fully qualified method name. Optionally, may contain arguments definition. 


### Path variables
In endpoints parts every sequence of chars prepended with `/:` prefix treated as a variable, e.g. everything after prefix and untill next `/` or EOL is taken as the value of this variable.
That mean defined route `/api/:myvar/set` in runtime backed by regular expression `^/api/([^/]+)/set$` and every path matched that RE will be handled by appropriate handler.


### Handlers arguments
Full form of argument is: `[Type]name[Container]`.

*Type* is real Java type. *Short*, *boolean*, *Long*, *char*, etc. In most cases it can be omitted, its important when two or more methods have similar signature. 

*name* is a name of the variable used, when its back-referenced from named context - query string, form or path. 

*Container* is a context, source of the named value. Its a predefined enumeration of:
- `Form` - Simple form or multipart form
- `Body` - Whole request's body is treated like a json object
- `Query` - Query-string of the request
- `Path` - Path of the request (Redundant marker, path vars are matched by names)
- `Cookie` - Cookies of the request
- `Request` - Special case, when handler need whole request as an object

Full form is rarely required, in most cases one or two parts can be omitted, when it evaluable.


### Examples
##### Primitive.

###### Handler:

```java
import org.logdoc.fairhttp.service.api.Controller;
import org.logdoc.fairhttp.service.http.Response;

public class MyController extends Controller {

    public Response getVersion() {
        return ok("1.0");
    }
}
```

###### Route:
```text
GET     /api/version    controllers.MyController.getVersion()
```

No path variables, no handlers arguments, nothing special. Parentheses are optional.

###### Request:  
`curl https://host.com/api/version`  

###### Response:
```text
< HTTP/1.1 200 Ok
< Content-Type: text/plain; charset=utf-8
1.0
```

---------------------------------------
##### Some path vars.

###### Handler:

```java
import org.logdoc.fairhttp.service.api.Controller;
import org.logdoc.fairhttp.service.http.Response;

public class MyController extends Controller {

    public Response sayHello(String name) {
        return ok("Hello, " + name);
    }
}
```

###### Route:
```text
GET     /api/hello/:myName    controllers.MyController.sayHello(myName)
```

Path variable `myName` is passed as the argument `name` to handler's method. Type declaration is omitted because its `String` by default (path is string itself). 
Container declaration is omitted because path is default and primary context to look for named variables.   
Equivalent full form:
```text
GET     /api/hello/:myName    controllers.MyController.sayHello([String] myName [Path])
```

###### Request:
`curl https://host.com/api/hello/Robert`  
###### Response:
```text
< HTTP/1.1 200 Ok
< Content-Type: text/plain; charset=utf-8
Hello, Robert
```




----------------------
##### Path, query and types  

###### Handler:

```java
import org.logdoc.fairhttp.service.api.Controller;
import org.logdoc.fairhttp.service.http.Response;

public class MyController extends Controller {

    public Response sayHello(String name, boolean isFirstTime) {
        String message;
        if (isFirstTime)
            message = "Hello again, ";
        else
            message = "Hello, ";

        return ok(message + name);
    }
}
```

###### Route:
```text
GET     /api/hello/:myName    controllers.MyController.sayHello(myName, first)
```
For variable `first` type requires boolean, FHS will parse it from String value; found in, if Container is omitted, then sequentally: Query or Cookies or Form or Multiform.

###### Equivalent full form:
```text
GET     /api/hello/:myName    controllers.MyController.sayHello([String] myName [Path], [boolean]first[Query])
```

###### Request:  
`curl https://host.com/api/hello/Robert`  
###### Response:
```text
< HTTP/1.1 200 Ok
< Content-Type: text/plain; charset=utf-8
Hello again, Robert
```

###### Request:  
`curl https://host.com/api/hello/Robert?first=true`  
###### Response:
```text
< HTTP/1.1 200 Ok
< Content-Type: text/plain; charset=utf-8
Hello, Robert
```


-----------------------------
##### Mixed names  

###### Handler:

```java
import org.logdoc.fairhttp.service.api.Controller;
import org.logdoc.fairhttp.service.http.Response;

public class MyController extends Controller {

    public Response sayHello(String firstName, String nickName, String lastName) {
        String greet = "";
        if (!firstName.trim().isBlank())
            greet = firstName + " ";

        if (nickName != null && !nickName.trim().isBlank())
            greet += "*" + nickName.trim() + "* ";

        if (lastName != null && !lastName.trim().isBlank())
            greet += lastName;

        return ok("Hello, " + greet.trim());
    }
}
```

###### Routes:
```text
GET      /api/hello/:name    controllers.MyController.sayHello(name, name[Query], name[Form])
POST     /api/hello/:name    controllers.MyController.sayHello(name, name[Query], name[Form])
```
###### Request:  
`curl https://host.com/api/hello/Robert?name=TheKing`  
###### Response:
```text
< HTTP/1.1 200 Ok
< Content-Type: text/plain; charset=utf-8
Hello, Robert *TheKing*
```


Lets add one more:  
###### Request:  
`curl -X POST https://host.com/api/hello/Robert?name=TheKing -F 'name=Johnson'`  
###### Response:
```text
< HTTP/1.1 200 Ok
< Content-Type: text/plain; charset=utf-8
Hello, Robert *TheKing* Johnson
```


----------------------------
##### Complex types  


###### DTO:
```java
package dto;

public class LoginRequest {
    public String login;
    public String password;
}
```
###### Handler:

```java
import org.logdoc.fairhttp.service.api.Controller;
import org.logdoc.fairhttp.service.http.Http;
import org.logdoc.fairhttp.service.http.Response;

public class MyController extends Controller {

    public Response doLogin(LoginRequest dto, String token, Http.Request request) {
        if (token != null && isTokenValid(token))
            return ok("Welcome, frequenter");

        if (request.header("my_token") != null && isTokenValid(request.header("my_token")))
            return ok("Welcome, frequenter");

        if (dto != null && isLoginSucceeded(dto))
            return ok("Welcome, stranger")
                    .withCookie(new Http.Builder()
                            .withName("my_token")
                            .withValue(tokenByLogin(dto.login))
                            .build());

        return status(400, "Shall you pass, stranger");
    }

    private boolean isLoginSucceeded(LoginRequest dto) {
        // .... implementation

        return true;
    }

    private boolean isTokenValid(String tokenData) {
        // .... implementation

        return true;
    }
}
```
This handler allows to sign in with different payloads. First, it tries to check if user came with a token in a cookie; then it checks if
token is passed via header; then it waits for a login form-data in a body of the request. Finally, if everything is failed, it returns error.


###### Route:
```text
POST     /api/signin    controllers.MyController.doLogin([Body], my_token[Cookie], [Request])
```

Lets explain all three arguments:
1. `[Body]` - First part is a type of the data. Second - name of the var - is omitted. Third - container is a payload of the request. Name is omitted because whole body treated as an object, there is no need to use names.  
2. `my_token[Cookie]` - Look in a cookies for one named `my_token`. Type is missed, because its defaults to String.
3. `[Request]` - special case of the container, when whole request is passed to a handler.
