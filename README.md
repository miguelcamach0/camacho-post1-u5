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


