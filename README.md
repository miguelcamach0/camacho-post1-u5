# Post-contenido — Unidad 5: Integración en Aplicaciones Web

## Descripción
Repositorio del post-contenido de la Unidad 5 de Patrones de Diseño
de Software. Un único proyecto Spring Boot (reservas-labs-api) para
la reserva de laboratorios de cómputo, con dos partes: una API REST
en capas (Entity, Repository, Service, Controller) sobre H2, y una
vista Thymeleaf (MVC clásico) que reutiliza el mismo Service.

## Parte 1 — Repository, Service y Controller REST
LaboratorioRepository y ReservaRepository extienden JpaRepository;
ReservaRepository agrega una consulta JPQL propia para detectar
solapamientos de horario. ReservaService concentra las reglas de
negocio (solapamiento, horario de atención, duración, cancelación
tardía). ReservaController y LaboratorioController exponen
/api/reservas y /api/laboratorios. Ver paquetes model/, repository/,
service/, exception/ y controller/.

## Parte 2 — Vista MVC con Thymeleaf
ReservaWebController expone /reservas con Thymeleaf, inyectando la
MISMA instancia de ReservaService que usa la API REST — sin Service
duplicado. ReservaWebExceptionHandler maneja las mismas excepciones
de dominio que GlobalRestExceptionHandler, con presentación distinta
(redirección con mensaje en vez de JSON). Ver paquete web/ y
templates/reservas/.

## Cómo ejecutar
```
$ mvn clean package
$ mvn spring-boot:run
```
API REST: http://localhost:8080/api/reservas
Vista MVC: http://localhost:8080/reservas

## Decisiones de diseño

### Punto de decisión 1 — Ubicación de la validación de solapamiento

Para validar que un laboratorio no tenga reservas en conflicto se analizaron dos alternativas: realizar toda la validación dentro de "ReservaService", obteniendo todas las reservas existentes del laboratorio y comparando los rangos de tiempo mediante lógica Java, o delegar la búsqueda de posibles conflictos al Repository mediante una consulta especializada que filtre directamente los solapamientos en la base de datos.

Se decidió implementar para este caso la segunda alternativa, utilizando el método "ReservaRepository.buscarSolapamientos". Esta decisión permite que la búsqueda de reservas incompatibles sea realizada directamente por la capa de persistencia mediante una consulta JPQL, evitando cargar en memoria todas las reservas asociadas a un laboratorio y compararlas manualmente desde la aplicación. Esto resulta más eficiente y escalable, especialmente considerando que el historial de reservas puede crecer con el tiempo.

Sin embargo, aunque el Repository realiza el filtrado de datos, no es responsable de tomar decisiones de negocio. Su función se limita a responder una consulta específica: "¿existen reservas activas que se solapen con este rango de tiempo para este laboratorio?". La decisión de permitir o rechazar la operación pertenece exclusivamente a "ReservaService", ya que representa una regla del dominio. Por esta razón, cuando el Repository devuelve resultados, es el Service quien determina que la reserva no puede crearse y lanza una ReservaConflictException con un mensaje descriptivo.

Esta separación mantiene claras las responsabilidades de cada capa: el Repository administra el acceso y consulta de información persistente, mientras que el Service concentra las reglas de negocio y controla el flujo de la operación. Si el Controller llamara directamente a "buscarSolapamientos()" sin pasar por "ReservaService", se mezclaría la lógica de negocio con la capa de presentación, haciendo que el controlador tuviera que decidir cuándo una reserva es válida o inválida. Además, cualquier otro componente que necesitara crear reservas podría evitar accidentalmente esta regla, generando inconsistencias en el sistema.


### Punto de decisión 2 — Reglas con y sin apoyo del Repository

Durante el diseño de ReservaService se identificó que no todas las reglas de negocio requieren consultar información almacenada en la base de datos. Para la creación de una reserva existen reglas que dependen únicamente de los datos propios del objeto que se está validando, como el horario permitido de atención y la duración máxima o mínima de la reserva, mientras que otras reglas dependen del estado actual del sistema, como verificar si otro usuario ya reservó el mismo laboratorio en ese intervalo de tiempo.

Para las validaciones de horario de atención (07:00 - 21:00) y duración permitida (30 minutos a 3 horas), se decidió implementar la lógica directamente dentro de "ReservaService" mediante el método "validarHorarioYDuracion", utilizando únicamente los atributos inicio y fin de la reserva actual. Esta decisión se toma porque la regla puede resolverse completamente con la información disponible en el objeto, sin necesidad de consultar otras entidades o acceder a la base de datos.

Involucrar al ReservaRepository en este tipo de validaciones sería innecesario, ya que la pregunta que se está resolviendo no depende de información persistida. Por ejemplo, para determinar si una reserva dura menos de 30 minutos no es necesario conocer las reservas existentes, los laboratorios registrados o cualquier otro dato almacenado. Delegar esta responsabilidad al Repository generaría un acoplamiento incorrecto entre una regla propia del dominio y la capa encargada de la persistencia.

Por otro lado, existen reglas que sí requieren apoyo del Repository cuando necesitan información que no pertenece al objeto actual. Un ejemplo es la validación de solapamiento de horarios, donde es necesario consultar otras reservas existentes para determinar si el laboratorio ya se encuentra ocupado. En ese caso, el Repository realiza la consulta de datos y el Service toma la decisión de negocio correspondiente.

Por lo tanto, el criterio utilizado para separar ambos tipos de reglas es el siguiente: si una validación depende únicamente del estado interno del objeto que se está procesando, debe resolverse dentro del Service sin involucrar la base de datos; si la regla requiere comparar información contra otros registros existentes en el sistema, debe apoyarse en una consulta del Repository, manteniendo la decisión final dentro del Service.

### Punto de decisión 3 — Cómo comparten Service el Controller MVC y el REST

Para la integración de la vista MVC con la API REST se evaluó cómo reutilizar la lógica de negocio existente sin duplicar las reglas implementadas en la capa Service. Las alternativas consideradas fueron mantener validaciones independientes dentro de "ReservaWebController" o crear un segundo servicio específico para la interfaz web, además del "ReservaService" utilizado por "ReservaController".

Se decidió reutilizar la misma clase "ReservaService" en ambos controladores. Tanto "ReservaController" como "ReservaWebController" reciben mediante inyección de dependencias la instancia de "ReservaService", administrada por Spring como un único bean de la aplicación. De esta forma, ambos puntos de entrada utilizan exactamente las mismas reglas de negocio relacionadas con la creación y cancelación de reservas, incluyendo la validación de solapamiento de horarios y las restricciones de duración y horario permitido.

Esta decisión mantiene la separación de responsabilidades definida en la arquitectura por capas: los controladores se encargan únicamente de gestionar la interacción con cada tipo de cliente, mientras que "ReservaService" concentra las decisiones propias del dominio. La diferencia entre ambos controladores se limita al formato de respuesta: "ReservaController" expone datos mediante una API REST en formato JSON, mientras que "ReservaWebController" retorna vistas Thymeleaf para la interfaz web.

Se descartó duplicar la lógica de validación dentro de "ReservaWebController" o crear un segundo servicio equivalente, debido a que esto generaría dos fuentes diferentes de reglas de negocio. Ante un cambio futuro en una regla como la validación de solapamientos, sería necesario modificar más de una ubicación, aumentando el riesgo de inconsistencias entre la aplicación web y la API REST.

La implementación se evidencia en:
- "ReservaController", donde el servicio es recibido por constructor para las operaciones REST.
- "ReservaWebController", donde se inyecta la misma clase "ReservaService" para las operaciones realizadas desde Thymeleaf.

### Punto de decisión 4 — Manejo de errores consistente entre MVC y REST

Durante la integración de la vista MVC con Thymeleaf se analizó cómo manejar las excepciones generadas por "ReservaService" sin perder la consistencia con la API REST implementada en la Parte 1. La principal decisión fue determinar si ambas superficies de presentación debían compartir un único manejador global de excepciones o si cada una debía tener un manejo específico según el tipo de respuesta que necesita generar.

Se decidió mantener dos manejadores de excepciones separados: "GlobalRestExceptionHandler" para los controladores REST y "ReservaWebExceptionHandler" para "ReservaWebController". Ambos utilizan las mismas excepciones de dominio ("ReservaConflictException" y "RecursoNoEncontradoException") generadas por "ReservaService", por lo que las reglas de negocio y el significado de los errores permanecen centralizados en la capa Service. La diferencia se encuentra únicamente en la forma en que cada interfaz presenta el resultado al usuario. Para la API REST, el manejo de errores debe responder mediante códigos HTTP y estructuras JSON, por lo que "GlobalRestExceptionHandler" transforma las excepciones en respuestas como "409 Conflict" o "404 Not Found". En cambio, la aplicación MVC utiliza vistas Thymeleaf, por lo que "ReservaWebExceptionHandler" redirige al usuario hacia la vista correspondiente utilizando mensajes flash que pueden mostrarse dentro del formulario HTML.

Se descartó utilizar un único "@RestControllerAdvice" para ambas superficies porque este mecanismo está orientado a respuestas REST y serializa los errores directamente como JSON, lo cual no corresponde al comportamiento esperado de una interfaz basada en vistas Thymeleaf. Aunque sería posible implementar un único manejador que detectara el tipo de cliente mediante condiciones como el encabezado "Accept", esto agregaría lógica condicional adicional dentro del manejo de excepciones y mezclaría responsabilidades de diferentes superficies de presentación.

## Herramientas utilizadas
- Java 17, Spring Boot 3.2, Spring Data JPA, H2, Thymeleaf
- Apache Maven, Postman/curl, Git, GitHub

## Conclusiones
El desarrollo de esta actividad permitió comprender la importancia de separar las responsabilidades dentro de una aplicación Spring Boot, identificando cómo los Controllers, Services y Repositories cumplen funciones diferentes y cómo una misma lógica de negocio puede ser reutilizada desde diferentes interfaces, como una API REST y una vista MVC con Thymeleaf. Uno de los aprendizajes más relevantes fue entender que las reglas de negocio deben permanecer centralizadas en la capa Service, mientras que los Repository únicamente deben encargarse de consultar la información necesaria y los Controllers de manejar la interacción con el usuario o cliente. La parte más compleja del proceso fue decidir dónde ubicar cada validación, especialmente al diferenciar entre reglas que dependían únicamente de los datos de la reserva (como duración y horario) y aquellas que requerían consultar el estado actual del sistema (como los solapamientos). 


