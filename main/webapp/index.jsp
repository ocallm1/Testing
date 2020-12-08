<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<html>
<body>
OK <br/>
<c:forEach items="${routeStatuses.keySet()}" var="routeName">
${routeName} : ${routeStatuses.get(routeName)}
<br/>
</c:forEach>
</body>
</html>