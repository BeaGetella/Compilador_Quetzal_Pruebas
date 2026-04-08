correr el compilador: 



\------------------------

\------------------------

si da error al iniciarlo 

Get-ChildItem "C:\\Program Files\\JetBrains" -Recurse -Filter "mvn.cmd" 2>$null | Select-Object FullName

\-----------------------------------------------





para generar el jar

\& "C:\\Program Files\\JetBrains\\IntelliJ IDEA Community Edition 2024.3.2\\plugins\\maven\\lib\\maven3\\bin\\mvn.cmd" package -DskipTests











1- se crea el qz 

\--se coloca esto en terminal 

2- java -jar target/compilador-quetzal.jar "C:\\Users\\iglesiadecristo\\OneDrive\\Escritorio\\quetzal\\Output\\Condicional.qz"

este siempreeeeee



3- esto jala el output 

cd output

java Condicional

```



Deberías ver:

```

Mayor de edad





\-----------------------

\--ESTE PARA CORRER EL QUETZAL EN VISUAL 

"C:\\Users\\iglesiadecristo\\OneDrive\\Escritorio\\quetzal\\Condicional.qz"











PARA PROBAR EN MI PC ES OSEA EN LA TERMINAL 
java -jar target/compilador-quetzal.jar "C:\\Users\\iglesiadecristo\\OneDrive\\Escritorio\\quetzal\\Output\\Condicional.qz"

