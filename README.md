# nplusone

Encuentra el N+1 en un log de SQL: la consulta que en vez de correr una vez
corrio una por cada fila de una lista.

Es el problema de rendimiento mas comun en cualquier backend con ORM, y el mas
facil de no ver: en desarrollo la lista tiene tres filas y la pagina vuela; en
produccion tiene ochocientas y el endpoint se cae. No aparece en las pruebas
porque no rompe nada. Solo se pone lento.

```
java -jar nplusone.jar app.log
mvn test | java -jar nplusone.jar
```

## Que ve

Con el log de ejemplo que viene en `ejemplos/pedidos.log`:

```
$ nplusone ejemplos/pedidos.log

nplusone 0.1.0

21 sentencias, 3 formas distintas.

2 hallazgos.

[1] 12 lecturas repetidas, sentencias 1 a 12

    Despues de:
      select o1_0.id,o1_0.customer_id,o1_0.total,o1_0.created_at from orders o1_0 where o1_0.created_at>?
    Se repitio:
      select c1_0.id,c1_0.name,c1_0.email from customers c1_0 where c1_0.id=1

    Es el N+1: una consulta trajo la lista y despues se pidio cada
    elemento por separado. Traelos de una, con un join o cargando
    los ids juntos en un `in`.

[2] 8 escrituras una por una, sentencias 13 a 20

    Despues de:
      select c1_0.id,c1_0.name,c1_0.email from customers c1_0 where c1_0.id=12
    Se repitio:
      insert into order_tags (order_id,tag) values (44,'etiqueta-1')

    Cada fila viajo sola. Agrupalas en un lote para que sea un viaje
    y no 8.
```

## Como decide

Contar repeticiones no basta, y esa es toda la diferencia con un
`grep | sort | uniq -c`: una consulta puede correr mil veces en un dia sin que
eso sea un problema. Lo que delata al N+1 es **que las repeticiones vengan
pegadas**, justo despues de otra consulta que trajo la lista.

Van tres pasos:

1. **Sacar el SQL.** Cada equipo loguea distinto (Hibernate, p6spy,
   `JdbcTemplate`, o el SQL pelado), asi que no se parsea un formato: se busca
   el verbo SQL y se exige su pieza obligatoria (`from` para un select, `into`
   para un insert, `set` para un update). Una linea que diga
   `WARN delete failed for order 12` tiene el verbo y no es SQL, y por eso no
   pasa. Prefiero dejar escapar una sentencia rara a inventar hallazgos: un
   falso positivo le quita al reporte toda la autoridad.

2. **Reducirlo a su forma.** `where id=1` y `where id=2` son la misma consulta.
   Se quitan los literales y los numeros, y se colapsan las listas
   (`in (?, ?, ?)` y `in (?, ?)` son la misma). Los alias que numera Hibernate
   (`o1_0`) sobreviven, porque ahi el digito va pegado a una letra.

3. **Buscar rachas.** Las apariciones de cada forma se agrupan en rachas
   consecutivas. Se tolera un hueco (`--gap`, por omision 2) para que un bucle
   que por cada pedido consulta el cliente **y** la direccion siga contando como
   un solo bucle. Una racha que llega al umbral (`--threshold`, por omision 5)
   es un hallazgo.

El log no tiene que venir de una sola peticion, pero mientras menos ruido tenga
mas limpio sale el reporte.

## En el CI

Termina en 1 cuando encuentra algo, asi que sirve de reja:

```yaml
- name: Buscar N+1
  run: mvn -q test | java -jar nplusone.jar --threshold 10
```

Si el umbral todavia esta muy apretado para lo que hay hoy, `--no-fail` reporta
igual pero termina en 0.

## Opciones

| Opcion | Que hace |
|---|---|
| `-t, --threshold N` | repeticiones seguidas para reportar (por omision 5) |
| `-g, --gap N` | sentencias ajenas toleradas dentro de una racha (por omision 2) |
| `-j, --json` | reporte en JSON, para que lo lea otra herramienta |
| `--no-fail` | termina en 0 aunque haya hallazgos |
| `-h, --help` | la ayuda |
| `-v, --version` | la version |

Codigos de salida: `0` sin hallazgos, `1` con hallazgos, `2` error de uso o de
lectura.

## Lo que no hace

- No se conecta a la base de datos ni a la aplicacion: lee texto y ya. Eso lo
  deja servir igual para un log de produccion de la semana pasada.
- No mide tiempo. Una consulta lenta que corre una vez no es asunto suyo.
- Un log con varias peticiones concurrentes entrelazadas ensucia las rachas.
  Si el log trae el hilo, filtralo antes: `grep 'handler-1' app.log | nplusone`.

## Correr y construir

Requiere Java 21.

```
./gradlew test
./gradlew installDist
./build/install/nplusone/bin/nplusone ejemplos/pedidos.log
```

Sin dependencias en tiempo de ejecucion, a proposito: una herramienta que se
mete en el CI de otros no deberia arrastrarles un arbol de librerias para leer
un archivo de texto.

## Por dentro

- `Finding` es un **tipo sellado**: los hallazgos posibles estan cerrados y el
  compilador lo sabe. El `switch` que arma el reporte no lleva `default`, asi
  que el dia que aparezca otra clase de hallazgo el reporte deja de compilar en
  vez de quedarse callado.
- `Statement` y `Options` son **records**: datos inmutables sin ceremonia.
- `Main.run()` recibe las tres corrientes por parametro y devuelve el codigo de
  salida. Solo `main()` llama a `System.exit()`, y por eso las pruebas pueden
  correr la herramienta entera (argumentos, lectura, reporte y codigo) sin
  tumbar la JVM del test.

## Licencia

MIT.
