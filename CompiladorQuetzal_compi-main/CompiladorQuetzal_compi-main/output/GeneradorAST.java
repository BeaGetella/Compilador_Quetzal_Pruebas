import java.util.List;
import java.util.Map;

public class GeneradorAST {
    public static void main(String[] args) {
        // Representación exacta del JSON del arbolAST usando colecciones nativas de Java
        Map<String, Object> arbolAST = Map.of(
            "type", "Program",
            "name", "Demo",
            "body", List.of(
                // 1. { type: "VarDecl", name: "x", value: { type: "Number", value: 5 } }
                Map.of(
                    "type", "VarDecl", 
                    "name", "x", 
                    "value", Map.of("type", "Number", "value", 5)
                ),
                
                // 2. { type: "Print", expr: { type: "Binary", op: "+", left: ..., right: ... } }
                Map.of(
                    "type", "Print",
                    "expr", Map.of(
                        "type", "Binary",
                        "op", "+",
                        "left", Map.of("type", "String", "value", "x = "),
                        "right", Map.of("type", "Identifier", "name", "x")
                    )
                ),
                
                // 3. { type: "If", test: ..., then: ..., else: ... }
                Map.of(
                    "type", "If",
                    "test", Map.of(
                        "type", "Binary",
                        "op", ">",
                        "left", Map.of("type", "Identifier", "name", "x"),
                        "right", Map.of("type", "Number", "value", 3)
                    ),
                    "then", List.of(
                        Map.of("type", "Print", "expr", Map.of("type", "String", "value", "x es mayor que 3"))
                    ),
                    "else", List.of(
                        Map.of("type", "Print", "expr", Map.of("type", "String", "value", "x no es mayor que 3"))
                    )
                ),
                
                // 4. { type: "While", test: ..., body: ... }
                Map.of(
                    "type", "While",
                    "test", Map.of(
                        "type", "Binary",
                        "op", ">",
                        "left", Map.of("type", "Identifier", "name", "x"),
                        "right", Map.of("type", "Number", "value", 0)
                    ),
                    "body", List.of(
                        Map.of("type", "Print", "expr", Map.of("type", "Identifier", "name", "x")),
                        Map.of(
                            "type", "Assign",
                            "name", "x",
                            "value", Map.of(
                                "type", "Binary",
                                "op", "-",
                                "left", Map.of("type", "Identifier", "name", "x"),
                                "right", Map.of("type", "Number", "value", 1)
                            )
                        )
                    )
                )
            )
        );

        // Ejemplo de cómo recorrer el cuerpo (body) del AST en tu compilador
        System.out.println("AST cargado exitosamente para el programa: " + arbolAST.get("name"));
        System.out.println("---------------------------------------------------------");
        
        List<Map<String, Object>> body = (List<Map<String, Object>>) arbolAST.get("body");
        for (Map<String, Object> nodo : body) {
            System.out.println("Nodo detectado en el AST -> Tipo: " + nodo.get("type"));
        }
    }
}