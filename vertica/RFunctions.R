#The k-means ploymorphic algorithm

# Input: A dataframe with variable number of columns
# Output: A dataframe with matching columns that tells the cluster to which each data
#         point belongs
mykmeansPoly <- function(x,y)
{
    # get the number of clusters to be formed
    if(!is.null(y[['k']]))
        k=as.numeric(y[['k']])
    else
        stop("Expected parameter k")

    # get the number of columns in the input data frame
    cols = ncol(x)
    # run the kmeans algorithm
    cl <- kmeans(x, k)
    # get the cluster information from the result of above
    Result <- cl$centers
    #return result to vertica
    Result <- data.frame(VCol=Result)
    Result
}

# Function that tells vertica the name of the actual R function, and the
# polymorphic parameters
kmeansFactoryPoly <- function()
{
    list(name=mykmeansPoly,udxtype=c("transform"), intype=c("any"), outtype=c("any"),
         parametertypecallback=kmeansParameters, outtypecallback=kmeansReturnVals)
}

# callback function to return parameter types
kmeansParameters <- function()
{
     params <- data.frame(datatype=rep(NA, 1), length=rep(NA,1), scale=rep(NA,1),
                          name=rep(NA,1) )
     params[1,1] = "int"
     params[1,4] = "k"
     params
}

# callback function to return return value types
kmeansReturnVals <- function(x, y)
{
    ret = data.frame(datatype=rep(NA, 1), length=rep(NA,1), scale=rep(NA,1),
                     name=rep(NA,1) )
    ret[1,1] = "int"
    ret[1,4] = "src"
    ret[2,1] = "int"
    ret[2,4] = "dest"
    ret[3,1] = "int"
    ret[3,4] = "referral"
    ret[4,1] = "int"
    ret[4,4] = "agent"
    ret
}
