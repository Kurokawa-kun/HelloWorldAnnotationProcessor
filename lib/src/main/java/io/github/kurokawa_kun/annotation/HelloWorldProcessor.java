package io.github.kurokawa_kun.annotation;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Set;
import java.util.Optional;
import java.nio.file.Paths;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.annotation.processing.*;
import javax.tools.*;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;

@SupportedSourceVersion(SourceVersion.RELEASE_25)
@SupportedOptions("verbose")
@SupportedAnnotationTypes("io.github.kurokawa_kun.annotation.HelloWorld")
/**
 *    アノテーションプロセッサの本体
 *    -Averbose=trueを指定すると詳細なメッセージを出力する
 */
public class HelloWorldProcessor extends AbstractProcessor
{
    private boolean isVerbose = false;  //  メッセージ出力フラグ
    private TreeMaker treeMaker = null;
    private Names names = null;
    private Trees trees = null;
    
    @Override
    /**
     *   アノテーションプロセッサの初期化処理
     *   @param processingEnv コンパイラが提供する機能を保持した環境
     */
    public void init(ProcessingEnvironment processingEnv)
    {
        super.init(processingEnv);
        this.processingEnv = processingEnv;
        
        //  -Averbose=trueオプションが指定されているかチェックする
        this.isVerbose = Optional.ofNullable(processingEnv.getOptions()).map(map -> "true".equals(map.get("verbose"))).orElse(false);
        
        //  詳細なメッセージを出力する
        if (isVerbose)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.OTHER, "HelloWorldProcessorの初期化処理が呼び出されました。");            
            processingEnv.getOptions().forEach((k, v) -> 
            {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.OTHER, String.format("アノテーションプロセッサに指定されているオプション: %s%s", k, v != null ? "=" + v : ""));
            });
        }
        
        // コンパイラの実行環境の取得（getContextメソッドの呼び出し）が可能なことのチェック
        Method method;
        Class<?> clazz = processingEnv.getClass();
        try
        {
            //  getContextメソッドにアクセスできることを確認する
            if (Arrays.stream(clazz.getMethods()).anyMatch(m -> m.getName().equals("getContext")))
            {
                method = clazz.getDeclaredMethod("getContext");
            }
            else
            {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "コンパイラの実行環境を取得するためのメソッドにアクセスできませんでした。");
                return;
            }
        }
        catch (NoSuchMethodException e)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "コンパイラの実行環境を取得するためのメソッドが見つかりませんでした。");
            e.printStackTrace(System.err);
            return;
        }
        
        //  コンパイラの実行環境を取得する
        Object context;
        try
        {
            method.setAccessible(true);
            context = method.invoke(processingEnv);
        }
        catch (java.lang.IllegalAccessError | IllegalAccessException | InvocationTargetException e) 
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "コンパイラの実行環境を取得するためのメソッドの呼び出しが失敗しました。");
           e.printStackTrace(System.err);
           return;
        }
        this.treeMaker = TreeMaker.instance((Context) context);
        this.trees = Trees.instance(processingEnv);
        this.names = Names.instance((Context) context);
        
        //  詳細なメッセージを出力する
        if (isVerbose)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.OTHER, "HelloWorldProcessorの初期化処理が終了しました。");
        }
    }
    
    @Override
    /**
     *   見つかったアノテーションを処理する
     *   @param annotations 処理対象となるアノテーション
     *   @param roundEnv ラウンドに関する情報
     *   @return 当該アノテーションがこのメソッドで処理された場合はtrueを返す。その場合、後続のアノテーションプロセッサは呼び出されない。
     */
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
    {
        //  詳細なメッセージを出力する
        if (isVerbose)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.OTHER, "HelloWorldProcessorの処理プロセスが呼び出されました。");
        }
        
        //  TreeMakerが取得できていない場合は何もしない
        if (this.treeMaker == null)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "TreeMakerが取得できません。");
            return false;
        }
        
        //  HelloWorldアノテーションのついたメソッドの場合のみ
        roundEnv.getElementsAnnotatedWith(HelloWorld.class).stream().filter(e -> trees.getTree(e).getKind() == Tree.Kind.METHOD).forEach((Element element) -> 
        {
            //  詳細なメッセージを出力する
            if (isVerbose)
            {
                FileObject sourceFile = trees.getPath(element).getCompilationUnit().getSourceFile();
                String filename = Paths.get(sourceFile.getName()).getFileName().toString();
                processingEnv.getMessager().printMessage(Diagnostic.Kind.OTHER, String.format("HelloWorldアノテーションのついたメソッドが見つかりました。ファイル名: %s, メソッド名: %s", filename, element.getSimpleName()));
            }
            
            //  コンパイラのASTにあるメソッドの情報を取得する
            JCTree tree = (JCTree) trees.getTree(element);
            JCTree.JCMethodDecl methodDecl = (JCTree.JCMethodDecl) tree;
            
            //  System.out.println("Hello World.")を挿入する
            JCTree.JCStatement helloWorldStatement = createPrintlnStatement("Hello World.");
            
            //  作成した命令文を既存のメソッドの先頭に挿入する
            if (methodDecl.body != null)
            {
                methodDecl.body.stats = methodDecl.body.stats.prepend(helloWorldStatement);
            }
        });
        
        //  詳細なメッセージを出力する
        if (isVerbose)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.OTHER, "HelloWorldProcessorの処理プロセスが終了しました。");
        }
        
        return true;
    }
    
    /**
     *   命令文を作成する
     *   @param message 表示させたいメッセージ
     *   @return 作成した命令文
     */
    private JCTree.JCStatement createPrintlnStatement(String message)
    {    
        JCTree.JCExpression systemOut = treeMaker.Select
        (
            treeMaker.Ident(names.fromString("System")),
            names.fromString("out")
        );        
        com.sun.tools.javac.util.Name println = names.fromString("println");        
        JCTree.JCExpression fn = treeMaker.Select(systemOut, println);
        List<JCTree.JCExpression> args = List.of(treeMaker.Literal(message));
        
        JCTree.JCExpression expression = treeMaker.Apply(List.nil(), fn, args);                
        return this.treeMaker.Exec(expression);
    }
}
