package roboslyq.learn.algorithm.sort;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 归并排序实现
 */
public class MergeSort implements Sort {

    @Override
    public ArrayList<Integer>  sort(ArrayList<Integer> arrayList) {
        return null;
    }

    @Override
    public Integer[] sort(Integer[] integers) {
        if(integers == null || integers.length==1){
            System.out.println("数组为空或者长度为1，不需要排序");
            return integers;
        }else {
            int size = integers.length;
            int start = 0;
            return mergeSort(integers,start,size);
        }
    }

    public Integer[] mergeSort(Integer[] integers,int start,int size){
        if(start < size-1){
            int firstArrayLength = size/2;
            int lastArrayLength = size - firstArrayLength;
            Integer[] integersFirst = copyInteger(integers,start,firstArrayLength);
            Integer[] integersLast =  copyInteger(integers,firstArrayLength,lastArrayLength);
            Integer[] resultFirst = mergeSort(integersFirst,0,firstArrayLength);
            Integer[] resultLast = mergeSort(integersLast,0,lastArrayLength);
            return merge(resultFirst,resultLast);
        }else{
            return integers;
        }
    }

    public Integer[] copyInteger(Integer[] integers,int start,int length){
        Integer[] integersResult = new Integer[length];
        for(int i=0;i<length;i++){
            integersResult[i] = integers[start+i];
        }
        return integersResult;
    }

    public Integer[] merge(Integer[] integers1,Integer[] integers2){
        Integer[] smallIngeter = integers1.length > integers2.length ? integers2 :integers1;
        Integer[] largeIngeter = integers1.length > integers2.length ? integers1 :integers2;
        int smallLength =smallIngeter.length;
        int largeLength = largeIngeter.length;
        int resultLength = smallLength+largeLength;
        Integer[] result = new Integer[resultLength];
        int i = 0 ;
        int largeStation = 0;
        int smallStation = 0;
        int finishFlag = 0; //0-小数组先结束，1-大数组先结束
        for(;i<resultLength;i++){
            if(smallStation<smallLength && largeStation< largeLength){
                if(smallIngeter[smallStation] <= largeIngeter[largeStation] ){
                    result[i] = smallIngeter[smallStation];
                    smallStation++;
                }else{
                    result[i] = largeIngeter[largeStation];
                    largeStation++;
                }
            }else{
                if(smallStation == smallLength){
                    finishFlag = 0;
                }else{
                    finishFlag = 1;
                }
                break;
            }
        }
        if(finishFlag==0){
            for(;largeStation<largeLength;largeStation++){
                result[i] = largeIngeter[largeStation];
                i++;
            }
        }else{
            for(;smallStation<smallLength;smallStation++){
                result[i] = smallIngeter[smallStation];
                i++;
            }
        }
        return  result;
    }

    public static void main1(String[] args) {
        Integer[] integers1 = {1,4,5,7,9,};
        Integer[] integers2 = {2,3,6,8};
        MergeSort mergeSort = new MergeSort();
        Integer[] mergeResult = mergeSort.merge(integers1,integers2);
        System.out.println("--->"+mergeResult.length);
        for(int i=0;i<mergeResult.length;i++){
            System.out.println(i+"--->"+mergeResult[i]);
        }
    }

    public static void main(String[] args) {
        Integer[] integers1 = {1,4,23,45,65,21,19,32,34,15,50,8,5,7,9,9,2,3,6,8};
//        MergeSort mergeSort = new MergeSort();
//        Integer[] mergeResult = mergeSort.sort(integers1);
//        System.out.println("--->"+mergeResult.length);
//        for(int i=0;i<mergeResult.length;i++){
//            System.out.println(i+"--->"+mergeResult[i]);
//        }

        //Integer[] integers1 = {4,9,6,8,2,1,5};
        ArrayList<Integer> arrayList = new ArrayList(Arrays.asList(integers1));
        arrayList.sort(new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
               int j = o1 >= o2 ? 1:-1;
               return j;
            }
        });
        int j=0;
        for(Integer temp : arrayList){
            System.out.println(j+"---"+temp);
            j++;
        }
    }
}
